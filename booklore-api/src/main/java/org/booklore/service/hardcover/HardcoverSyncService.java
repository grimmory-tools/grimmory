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

                // Determine the status based on progress
                int statusId = progressPercent >= 99.0f ? STATUS_READ : STATUS_CURRENTLY_READING;

                // Calculate progress in pages
                int progressPages = 0;
                if (hardcoverBook.pages == null || hardcoverBook.pages == 0) {
                    log.warn("Hardcover sync failed: book {} has no page count information, cannot calculate progress in pages", bookId);
                    return;
                }
      
                progressPages = Math.round((progressPercent / 100.0f) * hardcoverBook.pages);
                progressPages = Math.max(0, Math.min(hardcoverBook.pages, progressPages));
                
                log.info("Progress calculation: userId={}, progressPercent={}%, totalPages={}, progressPages={}", 
                        userId, progressPercent, hardcoverBook.pages, progressPages);

                // Step 1: Add/update the book in user's library
                Integer bookIdInt = Integer.parseInt(hardcoverBook.bookId);
                Integer userBookId = insertOrGetUserBook(bookIdInt, hardcoverBook.editionId, statusId);
                if (userBookId == null) {
                    log.warn("Hardcover sync failed: could not get user_book_id for book {}", bookId);
                    return;
                }

                // Step 2: Create or update the reading progress
                boolean isFinished = progressPercent >= 99.0f;
                boolean success = upsertReadingProgress(userBookId, hardcoverBook.editionId, progressPages, isFinished);
                
                if (success) {
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
     * Insert a book into the user's library or get existing user_book_id.
     */
    private Integer insertOrGetUserBook(Integer bookId, Integer editionId, int statusId) {
        String mutation = """
            mutation InsertUserBook($object: UserBookCreateInput!) {
              insert_user_book(object: $object) {
                user_book {
                  id
                }
                error
              }
            }
            """;

        Map<String, Object> bookInput = new java.util.HashMap<>();
        bookInput.put("book_id", bookId);
        bookInput.put("status_id", statusId);
        bookInput.put("date_added", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (editionId != null) {
            bookInput.put("edition_id", editionId);
        }

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(mutation);
        request.setVariables(Map.of("object", bookInput));

        try {
            Map<String, Object> response = executeGraphQL(request);
            log.debug("insert_user_book response: {}", response);
            if (response == null) return null;

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            Map<String, Object> insertResult = (Map<String, Object>) data.get("insert_user_book");
            if (insertResult == null) return null;

            // Check for error (might mean book already exists)
            String error = (String) insertResult.get("error");
            if (error != null && !error.isBlank()) {
                log.debug("insert_user_book returned error: {} - book may already exist, trying to find it", error);
                return findExistingUserBook(bookId);
            }

            Map<String, Object> userBook = (Map<String, Object>) insertResult.get("user_book");
            if (userBook == null) return null;

            Object idObj = userBook.get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).intValue();
            }

            return null;

        } catch (RestClientException e) {
            log.warn("Failed to insert user_book: {}", e.getMessage());
            // Try to find existing
            return findExistingUserBook(bookId);
        }
    }

    /**
     * Find an existing user_book entry for a book.
     */
    private Integer findExistingUserBook(Integer bookId) {
        String query = """
            query FindUserBook($bookId: Int!) {
              me {
                user_books(where: {book_id: {_eq: $bookId}}, limit: 1) {
                  id
                }
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("bookId", bookId));

        try {
            Map<String, Object> response = executeGraphQL(request);
            if (response == null) return null;

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            Map<String, Object> me = (Map<String, Object>) data.get("me");
            if (me == null) return null;

            List<Map<String, Object>> userBooks = (List<Map<String, Object>>) me.get("user_books");
            if (userBooks == null || userBooks.isEmpty()) return null;

            Object idObj = userBooks.getFirst().get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).intValue();
            }

            return null;

        } catch (RestClientException e) {
            log.warn("Failed to find existing user_book: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create or update reading progress for a user_book.
     */
    private boolean upsertReadingProgress(Integer userBookId, Integer editionId, int progressPages, boolean isFinished) {
        log.info("upsertReadingProgress: userBookId={}, editionId={}, progressPages={}, isFinished={}",
                userBookId, editionId, progressPages, isFinished);

        // First, try to find existing user_book_read
        Integer existingReadId = findExistingUserBookRead(userBookId);

        if (existingReadId != null) {
            // Update existing
            log.info("Updating existing user_book_read: id={}", existingReadId);
            return updateUserBookRead(existingReadId, editionId, progressPages, isFinished);
        } else {
            // Create new
            log.info("Creating new user_book_read for userBookId={}", userBookId);
            return insertUserBookRead(userBookId, editionId, progressPages, isFinished);
        }
    }

    private Integer findExistingUserBookRead(Integer userBookId) {
        String query = """
            query FindUserBookRead($userBookId: Int!) {
              user_book_reads(where: {user_book_id: {_eq: $userBookId}}, limit: 1) {
                id
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("userBookId", userBookId));

        try {
            Map<String, Object> response = executeGraphQL(request);
            if (response == null) return null;

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            List<Map<String, Object>> reads = (List<Map<String, Object>>) data.get("user_book_reads");
            if (reads == null || reads.isEmpty()) return null;

            Object idObj = reads.getFirst().get("id");
            if (idObj instanceof Number) {
                return ((Number) idObj).intValue();
            }

            return null;

        } catch (RestClientException e) {
            log.warn("Failed to find existing user_book_read: {}", e.getMessage());
            return null;
        }
    }

    private boolean insertUserBookRead(Integer userBookId, Integer editionId, int progressPages, boolean isFinished) {
        String mutation = """
            mutation InsertUserBookRead($userBookId: Int!, $object: DatesReadInput!) {
              insert_user_book_read(user_book_id: $userBookId, user_book_read: $object) {
                user_book_read {
                  id
                }
                error
              }
            }
            """;

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
            "object", readInput
        ));

        try {
            Map<String, Object> response = executeGraphQL(request);
            log.info("insert_user_book_read response: {}", response);
            if (response == null) return false;

            if (response.containsKey("errors")) {
                log.warn("insert_user_book_read returned errors: {}", response.get("errors"));
                return false;
            }

            return true;

        } catch (RestClientException e) {
            log.error("Failed to insert user_book_read: {}", e.getMessage());
            return false;
        }
    }

    private boolean updateUserBookRead(Integer readId, Integer editionId, int progressPages, boolean isFinished) {
        String mutation = """
            mutation UpdateUserBookRead($id: Int!, $object: DatesReadInput!) {
              update_user_book_read(id: $id, object: $object) {
                user_book_read {
                  id
                  progress
                }
                error
              }
            }
            """;

        Map<String, Object> readInput = new java.util.HashMap<>();
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
            "id", readId,
            "object", readInput
        ));

        try {
            Map<String, Object> response = executeGraphQL(request);
            log.debug("update_user_book_read response: {}", response);
            if (response == null) return false;

            if (response.containsKey("errors")) {
                log.warn("update_user_book_read returned errors: {}", response.get("errors"));
                return false;
            }

            return true;

        } catch (RestClientException e) {
            log.error("Failed to update user_book_read: {}", e.getMessage());
            return false;
        }
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
    private Integer extractInteger(Object obj) {
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
}
