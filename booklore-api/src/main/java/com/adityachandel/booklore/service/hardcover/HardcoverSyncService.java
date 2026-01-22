package com.adityachandel.booklore.service.hardcover;

import com.adityachandel.booklore.model.dto.HardcoverSyncSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.metadata.parser.hardcover.GraphQLRequest;
import lombok.extern.slf4j.Slf4j;
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
            currentApiToken.set(userSettings.getHardcoverApiKey());

            try {
                if (progressPercent == null) {
                    log.debug("Hardcover sync skipped: no progress to sync");
                    return;
                }

                // Fetch book fresh within the async context to avoid lazy loading issues
                BookEntity book = bookRepository.findById(bookId).orElse(null);
                if (book == null) {
                    log.debug("Hardcover sync skipped: book {} not found", bookId);
                    return;
                }

                BookMetadataEntity metadata = book.getMetadata();
                if (metadata == null) {
                    log.debug("Hardcover sync skipped: book {} has no metadata", bookId);
                    return;
                }

                // Find the book on Hardcover - use stored ID if available
                HardcoverBookInfo hardcoverBook;
                if (metadata.getHardcoverBookId() != null) {
                    // Use the stored numeric book ID and fetch edition/page info from Hardcover
                    hardcoverBook = new HardcoverBookInfo();
                    hardcoverBook.bookId = metadata.getHardcoverBookId();
                    log.debug("Using stored Hardcover book ID: {}", hardcoverBook.bookId);

                    // Always fetch the default edition and page count from Hardcover
                    HardcoverBookInfo fetched = findHardcoverBookById(hardcoverBook.bookId);
                    if (fetched != null) {
                        hardcoverBook.editionId = fetched.editionId;
                        hardcoverBook.pages = fetched.pages;
                        log.debug("Fetched from Hardcover: editionId={}, pages={}", hardcoverBook.editionId, hardcoverBook.pages);
                    } else {
                        log.warn("Could not fetch edition info from Hardcover for book ID: {}", hardcoverBook.bookId);
                    }
                } else {
                    // Search by ISBN
                    hardcoverBook = findHardcoverBook(metadata);
                    if (hardcoverBook == null) {
                        log.debug("Hardcover sync skipped: book {} not found on Hardcover", bookId);
                        return;
                    }
                }

                // Determine the status based on progress
                int statusId = progressPercent >= 99.0f ? STATUS_READ : STATUS_CURRENTLY_READING;

                // Calculate progress in pages
                int progressPages = 0;
                if (hardcoverBook.pages != null && hardcoverBook.pages > 0) {
                    progressPages = Math.round((progressPercent / 100.0f) * hardcoverBook.pages);
                    progressPages = Math.max(0, Math.min(hardcoverBook.pages, progressPages));
                }
                log.info("Progress calculation: userId={}, progressPercent={}%, totalPages={}, progressPages={}", 
                        userId, progressPercent, hardcoverBook.pages, progressPages);

                // Step 1: Add/update the book in user's library
                Integer userBookId = insertOrGetUserBook(hardcoverBook.bookId, hardcoverBook.editionId, statusId);
                if (userBookId == null) {
                    log.warn("Hardcover sync failed: could not get user_book_id for book {}", bookId);
                    return;
                }

                // Step 2: Create or update the reading progress
                boolean isFinished = progressPercent >= 99.0f;
                boolean success = upsertReadingProgress(userBookId, hardcoverBook.editionId, progressPages, isFinished);
                
                if (success) {
                    log.info("Synced progress to Hardcover: userId={}, book={}, hardcoverBookId={}, progress={}% ({}pages)", 
                            userId, bookId, hardcoverBook.bookId, Math.round(progressPercent), progressPages);
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
     * Find a book on Hardcover by ISBN or hardcoverId.
     * Returns the numeric book_id, edition_id, and page count.
     */
    private HardcoverBookInfo findHardcoverBook(BookMetadataEntity metadata) {
        // Try ISBN first
        String isbn = metadata.getIsbn13();
        if (isbn == null || isbn.isBlank()) {
            isbn = metadata.getIsbn10();
        }
        
        if (isbn == null || isbn.isBlank()) {
            log.debug("No ISBN available for Hardcover lookup");
            return null;
        }

        try {
            String searchQuery = """
                query SearchBooks($query: String!) {
                  search(query: $query, query_type: "Book", per_page: 1, page: 1) {
                    results
                  }
                }
                """;

            GraphQLRequest request = new GraphQLRequest();
            request.setQuery(searchQuery);
            request.setVariables(Map.of("query", isbn));

            Map<String, Object> response = executeGraphQL(request);
            log.debug("Hardcover search response for ISBN {}: {}", isbn, response);
            if (response == null) {
                return null;
            }

            // Navigate the response to get book info
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            Map<String, Object> search = (Map<String, Object>) data.get("search");
            if (search == null) return null;

            Map<String, Object> results = (Map<String, Object>) search.get("results");
            if (results == null) return null;

            List<Map<String, Object>> hits = (List<Map<String, Object>>) results.get("hits");
            if (hits == null || hits.isEmpty()) return null;

            Map<String, Object> document = (Map<String, Object>) hits.getFirst().get("document");
            if (document == null) return null;

            // Extract book info
            HardcoverBookInfo info = new HardcoverBookInfo();
            
            // The 'id' field contains the numeric book ID
            Object idObj = document.get("id");
            if (idObj instanceof String) {
                info.bookId = Integer.parseInt((String) idObj);
            } else if (idObj instanceof Number) {
                info.bookId = ((Number) idObj).intValue();
            }
            
            // Get page count
            Object pagesObj = document.get("pages");
            if (pagesObj instanceof Number) {
                info.pages = ((Number) pagesObj).intValue();
            }

            // Try to get default_physical_edition_id from the search results
            Object defaultPhysicalEditionObj = document.get("default_physical_edition_id");
            if (defaultPhysicalEditionObj instanceof Number) {
                info.editionId = ((Number) defaultPhysicalEditionObj).intValue();
            } else if (defaultPhysicalEditionObj instanceof String) {
                try {
                    info.editionId = Integer.parseInt((String) defaultPhysicalEditionObj);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // If no default physical edition found, try to look up edition by ISBN as fallback
            if (info.bookId != null && info.editionId == null) {
                EditionInfo edition = findEditionByIsbn(info.bookId, isbn);
                if (edition != null) {
                    info.editionId = edition.id;
                }
            }

            // Fetch page count from the edition (prioritizing edition page count over book-level page count)
            if (info.editionId != null) {
                EditionInfo edition = findEditionById(info.editionId);
                if (edition != null && edition.pages != null && edition.pages > 0) {
                    info.pages = edition.pages;
                    log.debug("Using page count from edition {}: {} pages", info.editionId, info.pages);
                }
            }

            log.info("Found Hardcover book: bookId={}, editionId={}, pages={}", 
                    info.bookId, info.editionId, info.pages);

            return info.bookId != null ? info : null;

        } catch (Exception e) {
            log.warn("Failed to search Hardcover by ISBN {}: {}", isbn, e.getMessage());
            return null;
        }
    }

    /**
     * Find an edition by ISBN for a given book.
     * This queries Hardcover's editions table to match by ISBN.
     */
    private EditionInfo findEditionByIsbn(Integer bookId, String isbn) {
        String query = """
            query FindEditionByIsbn($bookId: Int!, $isbn: String!) {
              editions(where: {
                book_id: {_eq: $bookId},
                _or: [
                  {isbn_10: {_eq: $isbn}},
                  {isbn_13: {_eq: $isbn}}
                ]
              }, limit: 1) {
                id
                pages
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("bookId", bookId, "isbn", isbn));

        try {
            Map<String, Object> response = executeGraphQL(request);
            log.debug("Edition lookup response: {}", response);
            if (response == null) return null;

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            List<Map<String, Object>> editions = (List<Map<String, Object>>) data.get("editions");
            if (editions == null || editions.isEmpty()) return null;

            Map<String, Object> edition = editions.getFirst();
            EditionInfo info = new EditionInfo();
            
            Object idObj = edition.get("id");
            if (idObj instanceof Number) {
                info.id = ((Number) idObj).intValue();
            }
            
            Object pagesObj = edition.get("pages");
            if (pagesObj instanceof Number) {
                info.pages = ((Number) pagesObj).intValue();
            }

            return info.id != null ? info : null;

        } catch (Exception e) {
            log.debug("Failed to find edition by ISBN: {}", e.getMessage());
            return null;
        }
    }

    private EditionInfo findEditionById(Integer editionId) {
        String query = """
            query FindEditionById($editionId: Int!) {
              editions(where: {id: {_eq: $editionId}}, limit: 1) {
                id
                pages
              }
            }
            """;

        GraphQLRequest request = new GraphQLRequest();
        request.setQuery(query);
        request.setVariables(Map.of("editionId", editionId));

        try {
            Map<String, Object> response = executeGraphQL(request);
            if (response == null) return null;

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            if (data == null) return null;

            List<Map<String, Object>> editions = (List<Map<String, Object>>) data.get("editions");
            if (editions == null || editions.isEmpty()) return null;

            Map<String, Object> edition = editions.getFirst();
            EditionInfo info = new EditionInfo();

            Object idObj = edition.get("id");
            if (idObj instanceof Number) {
                info.id = ((Number) idObj).intValue();
            }

            Object pagesObj = edition.get("pages");
            if (pagesObj instanceof Number) {
                info.pages = ((Number) pagesObj).intValue();
            }

            return info.id != null ? info : null;

        } catch (Exception e) {
            log.debug("Failed to find edition by ID: {}", e.getMessage());
            return null;
        }
    }

    private HardcoverBookInfo findHardcoverBookById(Integer bookId) {
        String query = """
            query FindBookById($bookId: Int!) {
              books(where: {id: {_eq: $bookId}}, limit: 1) {
                id
                default_physical_edition_id
                pages
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

            List<Map<String, Object>> books = (List<Map<String, Object>>) data.get("books");
            if (books == null || books.isEmpty()) return null;

            Map<String, Object> book = books.getFirst();
            HardcoverBookInfo info = new HardcoverBookInfo();
            info.bookId = bookId;

            Object defaultPhysicalEditionObj = book.get("default_physical_edition_id");
            if (defaultPhysicalEditionObj instanceof Number) {
                info.editionId = ((Number) defaultPhysicalEditionObj).intValue();
            } else if (defaultPhysicalEditionObj instanceof String) {
                try {
                    info.editionId = Integer.parseInt((String) defaultPhysicalEditionObj);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            // Get pages from the book level first
            Object pagesObj = book.get("pages");
            if (pagesObj instanceof Number) {
                info.pages = ((Number) pagesObj).intValue();
            }

            // If we have an edition ID, fetch the page count from that edition
            if (info.editionId != null) {
                EditionInfo edition = findEditionById(info.editionId);
                if (edition != null && edition.pages != null && edition.pages > 0) {
                    info.pages = edition.pages;
                    log.debug("Using page count from default physical edition {}: {} pages", info.editionId, info.pages);
                }
            }

            return info.editionId != null || info.pages != null ? info : null;

        } catch (Exception e) {
            log.debug("Failed to find Hardcover book by ID {}: {}", bookId, e.getMessage());
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
     * Helper class to hold Hardcover book information.
     */
    private static class HardcoverBookInfo {
        Integer bookId;
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
