package org.booklore.service.hardcover;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import java.sql.Types;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookIdentifier;
import org.booklore.model.dto.HardcoverBookProgress;
import org.booklore.model.dto.HardcoverSyncSettings;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.metadata.parser.hardcover.GraphQLRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
    private static final int HARDCOVER_LIMIT = 1000;

    private final RestClient restClient;
    private final HardcoverSyncSettingsService hardcoverSyncSettingsService;
    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    // Thread-local to hold the current API token for GraphQL requests
    private final ThreadLocal<String> currentApiToken = new ThreadLocal<>();
    private AtomicBoolean hardcoverImportLock = new AtomicBoolean(false);

    @Autowired
    public HardcoverSyncService(HardcoverSyncSettingsService hardcoverSyncSettingsService, BookRepository bookRepository, UserBookProgressRepository userBookProgressRepository, EntityManager entityManager, JdbcTemplate jdbcTemplate) {
        this.hardcoverSyncSettingsService = hardcoverSyncSettingsService;
        this.bookRepository = bookRepository;
        this.userBookProgressRepository = userBookProgressRepository;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
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
                progressPages = Math.clamp(progressPages, 0, hardcoverBook.pages);

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
        Map<String, Object> variables = new HashMap<>();
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
        Map<String, Object> variables = new HashMap<>();
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
        Map<String, Object> variables = new HashMap<>();
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

    private @Nullable List<Map> getUserBooksFromHardcover() throws InterruptedException {
        ArrayList<Map> user_books = new ArrayList<>();
        boolean fetchedAllBooks = false;
        while (!fetchedAllBooks) {
            log.debug("Quering Hardcover for the user's books with offset {}", user_books.size());
            String query = String.format("""
                query GetReadBooks {
                    me {
                        user_books_aggregate {
                          aggregate {
                            count
                          }
                        },
                        user_books (offset: %s, limit: %s) {
                            book {
                                editions {
                                    isbn_13,
                                    isbn_10
                                }
                            }
                            edition_id
                            book_id,
                            rating,
                            last_read_date,
                            status_id
                        }
                    }
                }
                """, String.valueOf(user_books.size()), String.valueOf(HARDCOVER_LIMIT));
            GraphQLRequest request = new GraphQLRequest();
            request.setQuery(query);
            Map<String, Object> response = executeGraphQL(request);
            if (response == null) {
                return null;
            }
            // Navigate the response to get book info
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            ArrayList<Map> me = (ArrayList<Map>) data.get("me");
            if (me == null) return null;

            Map<String, Object> first = (Map<String, Object>) me.getFirst();
            if (first == null || first.size() == 0) return null;

            ArrayList<Map> more_user_books = (ArrayList<Map>) first.get("user_books");
            if (more_user_books == null || more_user_books.size() == 0) return null;

            Map<String, Object> user_books_aggregate = (Map<String, Object>) first.get("user_books_aggregate");
            if (user_books_aggregate == null) return null;

            Map<String, Object> aggregate = (Map<String, Object>) user_books_aggregate.get("aggregate");
            if (aggregate == null) return null;

            user_books.addAll(more_user_books);

            Integer aggregate_count = (Integer) aggregate.get("count");
            if (aggregate_count == null) return null;

            if (aggregate_count - user_books.size() > 0) {
                // Respect the rate limit, which is 60 requests per minute (or 1 request per second)
                Thread.sleep(1000);
            } else {
                fetchedAllBooks = true;
            }
        }
        return user_books;
    }

    /**
     * Insert a new user_book and a corresponding user_book_read entry. This is used when there is no existing user_book for the book.
     * Sets the user_book status to "currently reading". Hardcover automatically creates a user_book_read.
     * @param bookId the Hardcover book ID to add to the user's library
     * @param editionId the edition ID to use for the user_book and user_book_read entries
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

        Map<String, Object> bookInput = new HashMap<>();
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

        Map<String, Object> bookInput = new HashMap<>();
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

        Map<String, Object> bookInput = new HashMap<>();
        bookInput.put("edition_id", editionId);
        bookInput.put("status_id", isFinished ? STATUS_READ : STATUS_CURRENTLY_READING);

        Map<String, Object> readInput = new HashMap<>();
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

        Map<String, Object> readInput = new HashMap<>();
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
     * Import all the data related to the books from an account from Hardcover
     * @param userId The ID of the Booklore user
     */
    @Async
    @Transactional
    public void importHardcoverData(Long userId, boolean overwriteData) {
        log.info("Hardcover import triggered");
        if (hardcoverImportLock.compareAndSet(false, true)) {        // Get user's Hardcover settings
            try {
                HardcoverSyncSettings userSettings = hardcoverSyncSettingsService.getSettingsForUserId(userId);
                if (!isHardcoverSyncEnabledForUser(userSettings)) {
                    log.trace("Hardcover sync skipped for user {}: not enabled or no API token configured", userId);
                    return;
                }
                // Set the user's API token for this sync operation
                currentApiToken.set(userSettings.getHardcoverApiKey());

                List<Map> user_books = getUserBooksFromHardcover();
                if (user_books == null) {
                    return;
                }
                Map<String, HardcoverBookProgress> allIsbns10 = new HashMap<>();
                Map<String, HardcoverBookProgress> allIsbns13 = new HashMap<>();
                Map<String, HardcoverBookProgress> hardcoverIds = new HashMap<>();
                ArrayList<HardcoverBookProgress> hardcoverData = parseHardcoverResponse(user_books, allIsbns10, allIsbns13, hardcoverIds);
                createNewProgressRecords(userId, allIsbns10, allIsbns13, hardcoverIds, hardcoverData);
                if (overwriteData) {
                    updateExistingProgress(userId, allIsbns10, allIsbns13, hardcoverIds, hardcoverData);
                }
                log.info("Hardcover import done");
            } catch (Exception e) {
                log.warn("Failed to get user's hardcover books: {}", e.getMessage());
            } finally {
                hardcoverImportLock.set(false);
            }
        } else {
            throw ApiError.TOO_MANY_HARDCOVER_IMPORTS.createException();
        }
    }

    private void createNewProgressRecords(Long userId, Map<String, HardcoverBookProgress> allIsbns10, Map<String, HardcoverBookProgress> allIsbns13, Map<String, HardcoverBookProgress> hardcoverIds, ArrayList<HardcoverBookProgress> hardcoverBooks) {
        List<BookIdentifier> noProgressBookIds = userBookProgressRepository.findMissingProgressBookIdsByHardcoverId(userId, allIsbns10.keySet(), allIsbns13.keySet(), hardcoverIds.keySet());
        if (noProgressBookIds.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO `user_book_progress` (`user_id`, `book_id`, `last_read_time`, read_status, `date_finished`, `personal_rating`) VALUES (?, ?, ?, ?, ?, ?)";
        List<BookIdentifier> toBeInsertedBooks = new ArrayList<>();
        for (BookIdentifier bookWithNoProgress : noProgressBookIds) {
            if (getHardcoverBook(allIsbns10, allIsbns13, hardcoverIds, bookWithNoProgress) != null) {
                toBeInsertedBooks.add(bookWithNoProgress);
            }
        }
        jdbcTemplate.batchUpdate(sql, toBeInsertedBooks, 100, (preparedStatement, bookIdentifier) -> {
            HardcoverBookProgress hardcoverBook = getHardcoverBook(allIsbns10, allIsbns13, hardcoverIds, bookIdentifier);

            if (hardcoverBook == null) return;
            preparedStatement.setInt(1, Math.toIntExact(userId));
            preparedStatement.setInt(2, bookIdentifier.getBookId());
            java.sql.Timestamp lastReadDate = hardcoverBook.getLastReadDate() == null ? null : new java.sql.Timestamp(hardcoverBook.getLastReadDate().getTime());
            if (lastReadDate != null) {
                preparedStatement.setTimestamp(3, lastReadDate);
                preparedStatement.setTimestamp(5, lastReadDate);
            } else {
                preparedStatement.setNull(3, Types.TIMESTAMP);
                preparedStatement.setNull(5, Types.TIMESTAMP);
            }
            preparedStatement.setString(4, hardcoverBook.getStatus().toString());
            if (hardcoverBook.getRating() != null) {
                preparedStatement.setInt(6, hardcoverBook.getRating());
            } else {
                preparedStatement.setNull(6, Types.INTEGER);
            }
        });
    }

    private void updateExistingProgress(Long userId, Map<String, HardcoverBookProgress> allIsbns10, Map<String, HardcoverBookProgress> allIsbns13, Map<String, HardcoverBookProgress> hardcoverIds, ArrayList<HardcoverBookProgress> hardcoverBooks) {
        List<BookIdentifier> booksWithExistingProgress = userBookProgressRepository.findExistingProgressBookIdsByIdentifiers(userId, allIsbns10.keySet(), allIsbns13.keySet(), hardcoverIds.keySet());
        for (BookIdentifier existingBookProgress : booksWithExistingProgress) {
            getHardcoverBook(allIsbns10, allIsbns13, hardcoverIds, existingBookProgress);
            HardcoverBookProgress hardcoverBook =
                    getHardcoverBook(allIsbns10, allIsbns13, hardcoverIds, existingBookProgress);

            if (hardcoverBook == null)
                continue;

            java.time.Instant lastReadDate = hardcoverBook.getLastReadDate() == null ? null :
                    hardcoverBook.getLastReadDate().toInstant();
            UserBookProgressEntity userBookProgressEntity =
                    entityManager.find(UserBookProgressEntity.class,
                                       existingBookProgress.getProgressId());
            userBookProgressEntity.setLastReadTime(lastReadDate);
            userBookProgressEntity.setDateFinished(lastReadDate);
            userBookProgressEntity.setReadStatus(hardcoverBook.getStatus());
            userBookProgressEntity.setPersonalRating(hardcoverBook.getRating());

            entityManager.merge(userBookProgressEntity);
        }
    }

    private HardcoverBookProgress getHardcoverBook(Map<String, HardcoverBookProgress> allIsbns10, Map<String, HardcoverBookProgress> allIsbns13, Map<String, HardcoverBookProgress> hardcoverIds, BookIdentifier existingBookProgress) {
        if (hardcoverIds.containsKey(existingBookProgress.getHardcoverBookId())) {
            return hardcoverIds.get(existingBookProgress.getHardcoverBookId());
        } else if (allIsbns10.containsKey(existingBookProgress.getIsbn10())) {
            return allIsbns10.get(existingBookProgress.getIsbn10());
        } else if (allIsbns13.containsKey(existingBookProgress.getIsbn13())) {
            return allIsbns13.get(existingBookProgress.getIsbn13());
        }
        return null;
    }

    private @Nullable ArrayList<HardcoverBookProgress> parseHardcoverResponse(List<Map> user_books, Map<String, HardcoverBookProgress> allIsbns10, Map<String, HardcoverBookProgress> allIsbns13, Map<String, HardcoverBookProgress> hardcoverIds) throws
            ParseException {
        ArrayList<HardcoverBookProgress> hardcoverBooks = new ArrayList<>();

        for (Map user_book : user_books) {
            HardcoverBookProgress hardcoverBook = new HardcoverBookProgress();
            hardcoverBook.setHardcoverId(((Integer) user_book.get("book_id")).toString());
            // Book status from Hardcover: https://docs.hardcover.app/api/graphql/schemas/books/#user-book-statuses
            ReadStatus readStatus = switch ((Integer) user_book.get("status_id")) {
                case 1 -> ReadStatus.UNREAD;
                case 2 -> ReadStatus.READING;
                case 3 -> ReadStatus.READ;
                case 4 -> ReadStatus.PAUSED;
                case 5 -> ReadStatus.ABANDONED;
                case 6 -> ReadStatus.WONT_READ;
                default -> ReadStatus.UNSET;
            };
            hardcoverBook.setStatus(readStatus);
            if (user_book.get("edition_id") != null) {
                hardcoverBook.setEditionId((Integer) user_book.get("edition_id"));
            }
            if (user_book.get("rating") != null) {
                Double rating = (Double) user_book.get("rating");
                // it's getting doubled, because ratings on Hardcover are out of 5, whereas the ratings on Booklore are out of 10.
                // So multiplying the rating by 2 properly scaled the rating from Hardcover to Booklore.
                Integer scaledRating = ((Double) (rating * 2)).intValue();
                hardcoverBook.setRating(scaledRating);
            }
            if (user_book.get("last_read_date") != null) {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-M-dd", Locale.ENGLISH);
                Date read_date = formatter.parse((String) user_book.get("last_read_date"));
                hardcoverBook.setLastReadDate(read_date);
            }
            if (user_book.get("book") != null) {
                Map<String, Object> book = (Map<String, Object>) user_book.get("book");
                if (book.get("editions") != null) {
                    List<Map> editions = (List<Map>) book.get("editions");

                    List<String> bookIsbns10 = new ArrayList<>();
                    List<String> bookIsbns13 = new ArrayList<>();
                    for (Map<String, Object> edition : editions) {
                        if (edition.get("isbn_10") != null) {
                            bookIsbns10.add((String) edition.get("isbn_10"));
                            allIsbns10.put((String) edition.get("isbn_10"), hardcoverBook);
                        }
                        if (edition.get("isbn_13") != null) {
                            bookIsbns13.add((String) edition.get("isbn_13"));
                            allIsbns13.put((String) edition.get("isbn_13"), hardcoverBook);
                        }
                    }
                    hardcoverBook.setIsbn10(bookIsbns10);
                    hardcoverBook.setIsbn13(bookIsbns13);
                }
            }
            hardcoverBooks.add(hardcoverBook);
            hardcoverIds.put(hardcoverBook.getHardcoverId(), hardcoverBook);
        }
        return hardcoverBooks;
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
            result.reads = new ArrayList<>();
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
