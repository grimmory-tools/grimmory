package org.booklore.service.hardcover;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.metadata.parser.hardcover.GraphQLRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Service to sync reading progress to Hardcover.
 * Uses per-user Hardcover API tokens for reading progress sync.
 * Each user can configure their own Hardcover API key in their sync settings.
 */
@Slf4j
@Service
public class HardcoverSyncService {

    private static final String HARDCOVER_API_URL = "https://api.hardcover.app/v1/graphql";
    private static final int STATUS_CURRENTLY_READING = 2;
    private static final int STATUS_READ = 3;

    private final RestClient restClient;
    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;
    private final BookRepository bookRepository;

    // Thread-local to hold the current API token for GraphQL requests
    private final ThreadLocal<String> currentApiToken = new ThreadLocal<>();

    @Autowired
    public HardcoverSyncService(HardcoverSyncSettingsService hardcoverSyncSettingsService, BookRepository bookRepository) {
        this.hardcoverSyncSettingsService = hardcoverSyncSettingsService;
        this.bookRepository = bookRepository;
        this.restClient = RestClient.builder()
                .baseUrl(HARDCOVER_API_URL)
                .build();
    }

    /**
     * Asynchronously sync Kobo reading progress to Hardcover.
     * This method is non-blocking and will not fail the calling process if sync fails.
     * Uses the user's personal Hardcover API key if configured.
     *
     * @param bookId The book ID to sync progress for
     * @param progressPercent The reading progress as a percentage (0-100)
     * @param userId The user ID whose reading progress is being synced
     */
    @Async
    @Transactional(readOnly = true)
    public void syncProgressToHardcover(Long bookId, Float progressPercent, Long userId) {
        try {
            // Get user's Hardcover settings
            HardcoverSyncSettings userSettings = hardcoverSyncSettingsService.getSettingsForUserId(userId);
            
            if (!isHardcoverSyncEnabledForUser(userSettings)) {
                log.trace("Hardcover sync skipped for user {}: not enabled or no API token configured", userId);
                return;
            }

            // Set the user's API token for this sync operation
            try {
                currentApiToken.set(userSettings.getHardcoverApiKey());

                if (progressPercent == null) {
                    log.debug("Hardcover sync skipped: no progress to sync");
                    return;
                }

                // Fetch book fresh within the async context to avoid lazy loading issues
                BookEntity book = bookRepository.findByIdWithMetadata(bookId).orElse(null);
                if (book == null) {
                    log.debug("Hardcover sync skipped: book {} not found", bookId);
                    return;
                }

                BookMetadataEntity metadata = book.getMetadata();
                if (metadata == null) {
                    log.debug("Hardcover sync skipped: book {} has no metadata", bookId);
                    return;
                }

                String hardcoverBookId = metadata.getHardcoverBookId();
                String isbn13 = metadata.getIsbn13();
                String isbn10 = metadata.getIsbn10();

                // Find the book and closest edition on Hardcover
                HardcoverBookInfo hardcoverBook = resolveHardcoverBook(hardcoverBookId, isbn13, isbn10);
                
                if (hardcoverBook == null) {
                    log.debug("Hardcover sync skipped: book {} not found on Hardcover", bookId);
                    return;
                }

                // Calculate progress in pages
                int progressPages = 0;
                if (hardcoverBook.pages == null || hardcoverBook.pages == 0) {
                    log.warn("Hardcover sync failed: book {} has no page count information, cannot calculate progress in pages", bookId);
                    return;
                }
      
                progressPages = Math.round((progressPercent / 100.0f) * hardcoverBook.pages);
                progressPages = Math.max(0, Math.min(hardcoverBook.pages, progressPages));

                boolean isFinished = progressPercent >= 99.0f;
                
                log.info("Progress calculation: userId={}, progressPercent={}%, totalPages={}, progressPages={}", 
                        userId, progressPercent, hardcoverBook.pages, progressPages);

                Integer hardcoverBookIdInt = extractInteger(hardcoverBook.bookId);
                
                // Check if user already has the book in their library and get existing reading progress
                UserBookWithReads userBook = getUserBookAndReads(hardcoverBookIdInt);
                
                // If user doesn't have the book in their library, insert it with the matching edition.
                if (userBook == null) {
                    // Inserting the user_book will automatically create a user_book_read entry with 0 progress and in the "Currently Reading" status, which we will then update with the correct progress below.
                    userBook = insertUserBook(hardcoverBookIdInt, hardcoverBook.editionId);
                } else if (userBook.statusId == STATUS_READ && isFinished) {
                    // If the user already has the book marked as read and the progress is finished, we can skip syncing to avoid creating duplicate reads for finished books.
                    // This also prevents accidentally resetting the finished date if the user had already marked it as read with a finished date.
                    log.info("User {} has book {} marked as read on Hardcover and progress is finished, skipping progress update", 
                        userId, bookId);
                    return;
                } else {
                    // If the user already has the book in their library, check if it is not Currently Reading status or if the edition is different from the one we are syncing.
                    // If it's not Currently Reading, we need to update the user_book status to Currently Reading to be able to update the reading progress. This will create a new user_book_read entry with 0 progress, which we will then update with the correct progress below.
                    if (userBook.statusId != STATUS_CURRENTLY_READING || userBook.editionId == null || !userBook.editionId.equals(hardcoverBook.editionId)) {
                        userBook = updateUserBook(userBook.id, hardcoverBook.editionId);
                    }
                }

                // If we couldn't get the existing user_book and we also failed to create it, we cannot proceed with syncing progress
                if (userBook == null) {
                    log.warn("Hardcover sync failed: could not get or create user_book entry for book {} (user {})", bookId, userId);
                    return;
                }

                boolean requiresNewReadEntry = true;

                // If the user already has the book in their library and is currently reading, check if the current reading activity matches the edition we want to update. 
                // If so, we can update the existing reading progress instead of creating a new one, which will keep the user's reading history cleaner.
                if (userBook.statusId == STATUS_CURRENTLY_READING && userBook.reads != null && !userBook.reads.isEmpty()) {
                    // Get the last reading activity, which matches the most recent reading activity. The user might have multiple reads if they restarted the book, but we want to update the most recent one.
                    UserBookReadInfo readInfo = userBook.reads.getLast();

                    // Only update if the edition matches (to avoid updating progress on a different edition if the user restarted the book with a different edition). If edition is missing, we assume it's the same edition and update it.
                    if (readInfo.editionId == null || (readInfo.editionId != null && readInfo.editionId.equals(hardcoverBook.editionId))) {
                        readInfo.progressPages = progressPages;
                        if (isFinished) {
                            readInfo.finishedAt = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                        }

                        // Update existing reading progress and the user book to match the edition and status based on the new progress
                        log.info("Updating existing reading progress for book {} (user {}), hardcoverBookId={}, hardcoverEditionId={}, progress={}% ({}pages)", 
                            bookId, userId, hardcoverBook.bookId, hardcoverBook.editionId, Math.round(progressPercent), progressPages);

                        boolean updatedRead = updateUserBookRead(userBook.id, readInfo);

                        if (!updatedRead) {
                          log.warn("Failed to update existing user_book_read entry for book {} (user {})", bookId, userId);
                          return;
                        }
                        requiresNewReadEntry = false;
                    }
                }

                // If the book is not being currently read or there is no matching reading activity, we want to create a new one
                // This should only happen if the user already has the book in their library and without a reading activity, or the latest reading activity is for another edition.
                if (requiresNewReadEntry) {
                    boolean insertedRead = insertUserBookRead(userBook.id, hardcoverBook.editionId, progressPages, isFinished);
                    requiresNewReadEntry = !insertedRead;
                }

                if (requiresNewReadEntry) {
                    log.warn("Hardcover sync failed: could not update user_book_read entry for book {} (user {})", bookId, userId);
                } else {
                    log.info("Synced progress to Hardcover: userId={}, book={}, hardcoverBookId={}, hardcoverEditionId={}, progress={}% ({}pages)", 
                        userId, bookId, hardcoverBook.bookId, hardcoverBook.editionId, Math.round(progressPercent), progressPages);
                }
            } finally {
                // Clean up thread-local
                currentApiToken.remove();
            }

        } catch (Exception e) {
            log.error("Failed to sync progress to Hardcover for book {} (user {}): {}", 
                    bookId, userId, e.getMessage());
        }
    }

    /**
     * Check if Hardcover sync is enabled for a specific user.
     */
    private boolean isHardcoverSyncEnabledForUser(HardcoverSyncSettings userSettings) {
        if (userSettings == null) {
            return false;
        }

        return userSettings.isHardcoverSyncEnabled() 
                && userSettings.getHardcoverApiKey() != null 
                && !userSettings.getHardcoverApiKey().isBlank();
    }

    private String getApiToken() {
        return currentApiToken.get();
    }

    /**
     * Resolve Hardcover book information using bookId and/or ISBN.
     * Returns bookId, editionId, and page count based on the following logic:
     * - If bookId + ISBN: Get book by ID, find edition by ISBN (with highest user_count), fallback to default editions
     * - If bookId only: Get book by ID, use default_ebook_edition, fallback to default_physical_edition
     * - If ISBN only: Find book with edition matching ISBN (with highest user_count)
     * 
     * @param hardcoverBookId The Hardcover book ID (can be null)
     * @param isbn13 The ISBN-13 (can be null)
     * @param isbn10 The ISBN-10 (can be null)
     * @return HardcoverBookInfo with bookId, editionId, and pages, or null if not found
     */
    private HardcoverBookInfo resolveHardcoverBook(String hardcoverBookId, String isbn13, String isbn10) {
        // No identifiers at all, it's impossible to resolve
        if ((hardcoverBookId == null || hardcoverBookId.isBlank()) && 
            (isbn13 == null || isbn13.isBlank()) && 
            (isbn10 == null || isbn10.isBlank())) {
            log.debug("Cannot resolve Hardcover book: no bookId or ISBN provided");
            return null;
        }
        
        // We have a specific bookId, try to resolve using it (with optional ISBN for edition matching)
        if (hardcoverBookId != null && !hardcoverBookId.isBlank()) {
            try {
                return resolveByBookId(Integer.parseInt(hardcoverBookId), isbn13, isbn10);
            } catch (NumberFormatException e) {
                log.warn("Invalid Hardcover book ID format: {}", hardcoverBookId);
                return null;
            }
        }
        
        // No bookId but we have ISBN, try to resolve book by ISBN
        return resolveByIsbn(isbn13, isbn10);
    }

    /**
     * Resolve book information when we have a bookId.
     * Tries to match edition by ISBN, then falls back to default editions.
     */
    private HardcoverBookInfo resolveByBookId(Integer bookId, String isbn13, String isbn10) {
        String query = """
            query GetBookWithEditions($bookId: Int!, $isbn13: String, $isbn10: String) {
              books(where: {id: {_eq: $bookId}}, limit: 1) {
                id
                pages
                default_ebook_edition {
                  id
                  pages
                }
                default_physical_edition {
                  id
                  pages
                }
                editions(where: {
                  _or: [
                    {isbn_13: {_eq: $isbn13}},
                    {isbn_10: {_eq: $isbn10}}
                  ]
                }, order_by: {users_count: desc}, limit: 1) {
                  id
                  pages
                }
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("bookId", bookId);
        variables.put("isbn13", (isbn13 != null && !isbn13.isBlank()) ? isbn13 : "");
        variables.put("isbn10", (isbn10 != null && !isbn10.isBlank()) ? isbn10 : "");
        request.setVariables(variables);

        try {
            Map<String, Object> response = executeGraphQL(request);
            if (response == null) {
                log.warn("No response from Hardcover for book ID {}", bookId);
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            List<Map<String, Object>> books = (List<Map<String, Object>>) data.get("books");
            if (books == null || books.isEmpty()) {
                log.warn("Book ID {} not found on Hardcover", bookId);
                return null;
            }

            Map<String, Object> book = books.getFirst();
            HardcoverBookInfo info = new HardcoverBookInfo();
            info.bookId = String.valueOf(bookId);

            // Try to get edition by ISBN
            List<Map<String, Object>> editions = (List<Map<String, Object>>) book.get("editions");
            if (editions != null && !editions.isEmpty()) {
                Map<String, Object> bestEdition = editions.getFirst();
                info.editionId = extractInteger(bestEdition.get("id"));
                info.pages = extractInteger(bestEdition.get("pages"));
                log.debug("Found edition by ISBN: editionId={}, pages={}", 
                    info.editionId, info.pages);
            }

            // Fallback to default_ebook_edition
            if (info.editionId == null) {
                Map<String, Object> defaultEbookEdition = (Map<String, Object>) book.get("default_ebook_edition");
                if (defaultEbookEdition != null && defaultEbookEdition.get("id") != null) {
                    info.editionId = extractInteger(defaultEbookEdition.get("id"));
                    info.pages = extractInteger(defaultEbookEdition.get("pages"));
                    log.debug("Using default_ebook_edition: editionId={}, pages={}", info.editionId, info.pages);
                }
            }

            // Fallback to default_physical_edition
            if (info.editionId == null) {
                Map<String, Object> defaultPhysicalEdition = (Map<String, Object>) book.get("default_physical_edition");
                if (defaultPhysicalEdition != null && defaultPhysicalEdition.get("id") != null) {
                    info.editionId = extractInteger(defaultPhysicalEdition.get("id"));
                    info.pages = extractInteger(defaultPhysicalEdition.get("pages"));
                    log.debug("Using default_physical_edition: editionId={}, pages={}", info.editionId, info.pages);
                }
            }

            // Fallback to book-level pages if edition has no pages
            if (info.pages == null) {
                info.pages = extractInteger(book.get("pages"));
            }

            if (info.editionId == null) {
                log.warn("No edition found for book ID {}", bookId);
                return null;
            }

            log.info("Resolved Hardcover book: bookId={}, editionId={}, pages={}", 
                info.bookId, info.editionId, info.pages);
            return info;

        } catch (Exception e) {
            log.error("Failed to resolve Hardcover book by ID {}: {}", bookId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Resolve book information when we only have ISBN.
     * Finds books with matching edition and picks the one with highest user_count.
     */
    private HardcoverBookInfo resolveByIsbn(String isbn13, String isbn10) {
        String query = """
            query GetBooksByIsbn($isbn13: String, $isbn10: String) {
              books(where: {
                editions: {
                  _or: [
                    {isbn_13: {_eq: $isbn13}},
                    {isbn_10: {_eq: $isbn10}}
                  ]
                }
              }, order_by: {editions_aggregate: {max: {users_count: desc}}}, limit: 1) {
                id
                pages
                editions(where: {
                  _or: [
                    {isbn_13: {_eq: $isbn13}},
                    {isbn_10: {_eq: $isbn10}}
                  ]
                }, order_by: {users_count: desc}, limit: 1) {
                  id
                  pages
                }
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("isbn13", (isbn13 != null && !isbn13.isBlank()) ? isbn13 : "");
        variables.put("isbn10", (isbn10 != null && !isbn10.isBlank()) ? isbn10 : "");
        request.setVariables(variables);

        try {
            Map<String, Object> response = executeGraphQL(request);
            if (response == null) {
                log.warn("No response from Hardcover for ISBN {} / {}", isbn13, isbn10);
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            List<Map<String, Object>> books = (List<Map<String, Object>>) data.get("books");
            if (books == null || books.isEmpty()) {
                log.warn("No books found on Hardcover with ISBN {} / {}", isbn13, isbn10);
                return null;
            }

            Map<String, Object> book = books.getFirst();
            List<Map<String, Object>> editions = (List<Map<String, Object>>) book.get("editions");
            if (editions == null || editions.isEmpty()) {
                log.warn("No valid editions found for ISBN {} / {}", isbn13, isbn10);
                return null;
            }

            Map<String, Object> edition = editions.getFirst();
            if (book == null || edition == null) {
                log.warn("Book or edition data missing for ISBN {} / {}", isbn13, isbn10);
                return null;
            }

            HardcoverBookInfo info = new HardcoverBookInfo();
            info.bookId = String.valueOf(extractInteger(book.get("id")));
            info.editionId = extractInteger(edition.get("id"));
            info.pages = extractInteger(edition.get("pages"));

            // Fallback to book-level pages if edition has no pages
            if (info.pages == null) {
                info.pages = extractInteger(book.get("pages"));
            }

            log.info("Resolved Hardcover book by ISBN: bookId={}, editionId={}, pages={}", 
                info.bookId, info.editionId, info.pages);
            return info;
        } catch (Exception e) {
            log.error("Failed to resolve Hardcover book by ISBN {} / {}: {}", isbn13, isbn10, e.getMessage());
            return null;
        }
    }

    /**
     * Get the user's user_book entry for the specified book ID, along with all associated user_book_read entries to check existing reading progress.
     * @param bookId the Hardcover book ID to fetch the user's book entry for
     * @return a UserBookWithReads object containing the user's book entry and associated reading progress, or null if not found
     * @throws Exception when an error occurs, distinguishing between "not found" (returns null) vs "error fetching from Hardcover" (throws exception)
     */
    private UserBookWithReads getUserBookAndReads(Integer bookId) throws Exception {
        String query = """
            query GetUserBookAndReads($bookId:Int!) {
                me {
                    user_books(where: {book_id:{_eq: $bookId}}) {
                        id
                        status_id
                        edition_id
                        user_book_reads {
                            id
                            edition_id
                            started_at
                            finished_at
                            progress
                            progress_pages
                        }
                    }
                }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("bookId", bookId);
        request.setVariables(variables);

        Map<String, Object> response = executeGraphQL(request);
        if (response == null) {
            log.warn("No response from Hardcover for book ID {}", bookId);
            throw new Exception("No response from Hardcover for book ID " + bookId);
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) throw new Exception("No data returned from Hardcover for book ID " + bookId);

        List<Map<String, Object>> meList = (List<Map<String, Object>>) data.get("me");
        if (meList == null || meList.isEmpty()) {
            log.debug("No user data returned from Hardcover for book ID {}", bookId);
            return null;
        }

        Map<String, Object> me = meList.getFirst();
        List<Map<String, Object>> userBooks = (List<Map<String, Object>>) me.get("user_books");
        if (userBooks == null || userBooks.isEmpty()) {
            log.debug("No user_book found for book ID {}", bookId);
            return null;
        }

        Map<String, Object> userBook = userBooks.getFirst();
        if (userBook == null) {
            log.debug("User_book entry is null for book ID {}", bookId);
            return null;
        }
        log.debug("Found user_book for book ID {}: {} {}", bookId, userBooks, userBook);

        return UserBookWithReads.fromMap(userBook);
    }

    /**
     * Insert a new user_book and a corresponding user_book_read entry. This is used when there is no existing user_book for the book.
     * Sets the user_book status to "currently reading". Hardcover automatically creates a user_book_read.
     * @param bookId the Hardcover book ID to add to the user's library
     * @param editionId the edition ID to use for the user_book and user_book_read entries
     * @param progressPages the number of pages read to set in the user_book_read entry
     * @param isFinished whether to set the user_book status to "read" and include a finished_at date in the user_book_read entry
     * @return the created UserBookWithReads object with the new user_book and user_book_read entries, or null if the insert failed
     */
    private UserBookWithReads insertUserBook(Integer bookId, Integer editionId) {
        String mutation = """
            mutation InsertUserBook($object: UserBookCreateInput!) {
                insert_user_book(object: $object) {
                    user_book {
                        id
                        status_id
                        edition_id
                        user_book_reads {
                            id
                            edition_id
                            started_at
                            finished_at
                            progress
                            progress_pages
                        }
                    }
                    error
                }
            }
            """;

        Map<String, Object> bookInput = new java.util.HashMap<>();
        bookInput.put("book_id", bookId);
        bookInput.put("edition_id", editionId);
        bookInput.put("status_id", STATUS_CURRENTLY_READING);
        
        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of(
            "object", bookInput
        ));

        try {
            Map<String, Object> response = executeGraphQL(request);
            log.trace("insert_user_book response: {}", response);
            if (response == null) return null;

            if (response.containsKey("errors")) {
                log.warn("Failed to insert user_book: {}", response.get("errors"));
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            Map<String, Object> insertResult = (Map<String, Object>) data.get("insert_user_book");
            if (insertResult == null) return null;

            String error = (String) insertResult.get("error");
            if (error != null && !error.isBlank()) {
                log.warn("Error inserting user_book: {}", error);
                return null;
            }
            
            Map<String, Object> userBook = (Map<String, Object>) insertResult.get("user_book");
            if (userBook == null) return null;

            return UserBookWithReads.fromMap(userBook);

        } catch (RestClientException e) {
            log.error("Failed to insert user_book: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Updates an existing user_book to set the edition_id and status_id based on the parameters, and returns the updated user_book with reads.
     * Notes: When updating the user_book status to "Currently Reading", Hardcover automatically creates a new user_book_read entry with 0 progress. When setting to "Read", it also sets the finished_at date on that user_book_read entry, but progress stays unchanged. 
     * @param userBookId the ID of the existing user_book
     * @param editionId the edition ID to use for the user_book_read entry
     * @return the updated UserBookWithReads object, or null if the update failed
     */
    private UserBookWithReads updateUserBook(Integer userBookId, Integer editionId) {
        // Updating the user book on Hardcover has some quirky behavior that we need to account for:
        // - If edition_id is changed, Hardcover updates the first non-finished user_book_read entry with the new edition_id.
        // - If status is changed to "Read", Hardcover sets the finished_at date on the last user_book_read entry, but does not update the progress or progress_pages.
        // Because we may be inserting a new user_book_read entry, we never set the book to finished and let the insertUserBookRead mutation handle the read status update.
        String mutation = """
            mutation UpdateUserBook($userBookId: Int!, $userBookObject: UserBookUpdateInput!) {
                update_user_book(id: $userBookId, object: $userBookObject) {
                    id
                    error
                    user_book {
                        id
                        status_id
                        edition_id
                        user_book_reads {
                            id
                            edition_id
                            started_at
                            finished_at
                            progress
                            progress_pages
                        }
                    }
                }
            }
            """;

        Map<String, Object> bookInput = new java.util.HashMap<>();
        bookInput.put("edition_id", editionId);
        bookInput.put("status_id", STATUS_CURRENTLY_READING);

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of(
            "userBookId", userBookId,
            "userBookObject", bookInput
        ));

        try {
            Map<String, Object> response = executeGraphQL(request);
            log.trace("update_user_book response: {}", response);
            if (response == null) return null;

            if (response.containsKey("errors")) {
                log.warn("update_user_book returned errors: {}", response.get("errors"));
                return null;
            }
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            Map<String, Object> updateResult = (Map<String, Object>) data.get("update_user_book");
            if (updateResult == null) return null;
            String error = (String) updateResult.get("error");

            if (error != null && !error.isBlank()) {
                log.warn("update_user_book returned error: {}", error);
                return null;
            }

            Map<String, Object> userBook = (Map<String, Object>) updateResult.get("user_book");
            if (userBook == null) return null;  

            return UserBookWithReads.fromMap(userBook);

        } catch (RestClientException e) {
            log.error("Failed to update user_book: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Insert a new user_book_read entry for an existing user_book.
     * This is used when there is no existing reading progress for the book, or when a new one should be created (e.g. user restarted the book).
     * This also updates the user_book entry to set the edition_id and status_id based on the parameters, in case it was missing or needs to be updated.
     * @param userBookId the ID of the existing user_book
     * @param editionId the edition ID to use for the user_book_read entry
     * @param progressPages the number of pages read to set in the user_book_read entry
     * @param isFinished whether to set the user_book_read entry as finished
     * @return true if the insert was successful, false otherwise
     */
    private boolean insertUserBookRead(Integer userBookId, Integer editionId, Integer progressPages, boolean isFinished) {
        String mutation = """
            mutation InsertUserBookRead($userBookId: Int!, $userBookReadObject: DatesReadInput!) {
                insert_user_book_read(user_book_id: $userBookId, user_book_read: $userBookReadObject) {
                    user_book_read {
                        id
                    }
                    error
                }
            }
            """;

        Map<String, Object> bookInput = new java.util.HashMap<>();
        bookInput.put("edition_id", editionId);
        bookInput.put("status_id", isFinished ? STATUS_READ : STATUS_CURRENTLY_READING);

        Map<String, Object> readInput = new java.util.HashMap<>();
        readInput.put("started_at", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        readInput.put("progress_pages", progressPages);
        if (isFinished) {
            readInput.put("finished_at", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        if (editionId != null) {
            readInput.put("edition_id", editionId);
        }

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of(
            "userBookId", userBookId,
            "userBookReadObject", readInput
        ));

        try {
            Map<String, Object> response = executeGraphQL(request);
            log.trace("insert_user_book_read response: {}", response);
            if (response == null) return false;

            if (response.containsKey("errors")) {
                log.warn("insert_user_book_read returned errors: {}", response.get("errors"));
                return false;
            }

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return false;
            Map<String, Object> insertResult = (Map<String, Object>) data.get("insert_user_book_read");
            if (insertResult == null) return false;
            String error = (String) insertResult.get("error");
            if (error != null && !error.isBlank()) {
                log.warn("insert_user_book_read returned error: {}", error);
                return false;
            }
          return true;

        } catch (RestClientException e) {
            log.error("Failed to insert user_book_read: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Updates an existing user_book_read entry with new progress information. This is used when there is already reading progress for the book, and we want to update it with new progress (e.g. user continued reading).
     * If the user_book_read is being updated to finished, the user_book status is automatically updated to "Read" by Hardcover.
     * @param userBookId the ID of the existing user_book
     * @param readInfo the entire user_book_read info. We need to pass the entire info because the API requires all fields to update, and we need to update the progress and finished_at fields based on the new progress.
     * @return true if the update was successful, false otherwise
     */
    private boolean updateUserBookRead(Integer userBookId, UserBookReadInfo readInfo) {
        String mutation = """
            mutation UpdateUserBookRead($userBookReadId: Int!, $userBookReadObject: DatesReadInput!) {
                update_user_book_read(id: $userBookReadId, object: $userBookReadObject) {
                    id
                    error
                }
            }
            """;

        Map<String, Object> readInput = new java.util.HashMap<>();
        readInput.put("edition_id", readInfo.editionId);
        readInput.put("started_at", readInfo.startedAt);
        readInput.put("progress_pages", readInfo.progressPages);
        if (readInfo.finishedAt != null) {
            readInput.put("finished_at", readInfo.finishedAt);
        }
        

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of(
            "userBookReadId", readInfo.id,
            "userBookReadObject", readInput
        ));

        Map<String, Object> response = executeGraphQL(request);

        log.trace("update_user_book_read response: {}", response);
        if (response == null) return false;
        if (response.containsKey("errors")) {
            log.warn("update_user_book_read returned errors: {}", response.get("errors"));
            return false;
        }

        if (response.containsKey("data")) {
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data != null) {
                Map<String, Object> updateUserBookReadResult = (Map<String, Object>) data.get("update_user_book_read");
                if (updateUserBookReadResult != null && updateUserBookReadResult.get("error") != null) {
                    log.warn("update_user_book_read returned error: {}", updateUserBookReadResult.get("error"));
                    return false;
                }
            }
        }
        return true;
    }

    private Map<String, Object> executeGraphQL(GraphQLRequest request) {
        try {
            return restClient.post()
                    .uri("")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getApiToken())
                    .body(request)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.error("GraphQL request failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Helper method to safely extract Integer from various number types or strings.
     */
    private static Integer extractInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Float extractFloat(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) {
            return ((Number) obj).floatValue();
        }
        if (obj instanceof String) {
            try {
                return Float.parseFloat((String) obj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Helper class to hold Hardcover book information.
     */
    private static class HardcoverBookInfo {
        String bookId;
        Integer editionId;
        Integer pages;
    }

    /**
     * Helper class to hold edition information.
     */
    private static class EditionInfo {
        Integer id;
        Integer pages;
    }

    private static class UserBookWithReads {
        Integer id;
        Integer statusId;
        Integer editionId;
        List<UserBookReadInfo> reads;

        static UserBookWithReads fromMap(Map<String, Object> userBook) {
            UserBookWithReads result = new UserBookWithReads();
            result.reads = new java.util.ArrayList<>();
            result.id = extractInteger(userBook.get("id"));
            result.statusId = extractInteger(userBook.get("status_id"));
            result.editionId = extractInteger(userBook.get("edition_id"));

            List<Map<String, Object>> reads = (List<Map<String, Object>>) userBook.get("user_book_reads");
            if (reads != null) {
                for (Map<String, Object> read : reads) {
                    UserBookReadInfo readInfo = new UserBookReadInfo();
                    readInfo.id = extractInteger(read.get("id"));
                    readInfo.editionId = extractInteger(read.get("edition_id"));
                    readInfo.startedAt = (String) read.get("started_at");
                    readInfo.finishedAt = (String) read.get("finished_at");
                    readInfo.progress = extractFloat(read.get("progress"));
                    readInfo.progressPages = extractInteger(read.get("progress_pages"));
                    result.reads.add(readInfo);
                }
            }

            return result;
        }
    }

    private static class UserBookReadInfo {
        Integer id;
        Integer editionId;
        String startedAt;
        String finishedAt;
        Integer progressPages;
        Float progress;
    }
}
