package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.*;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.dto.response.BookDeletionResponse;
import org.booklore.model.dto.response.BookStatusUpdateResponse;
import org.booklore.model.dto.response.BookSyncResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.*;
import org.booklore.repository.BookFileRepository;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.progress.ReadingProgressService;
import org.booklore.service.FileStreamingService;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.booklore.model.enums.AuditAction;
import org.booklore.service.audit.AuditService;

@Slf4j
@AllArgsConstructor
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final PdfViewerPreferencesRepository pdfViewerPreferencesRepository;
    private final CbxViewerPreferencesRepository cbxViewerPreferencesRepository;
    private final NewPdfViewerPreferencesRepository newPdfViewerPreferencesRepository;
    private final FileService fileService;
    private final BookMapper bookMapper;
    private final UserBookProgressRepository userBookProgressRepository;
    private final AuthenticationService authenticationService;
    private final BookQueryService bookQueryService;
    private final ReadingProgressService readingProgressService;
    private final BookDownloadService bookDownloadService;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final BookUpdateService bookUpdateService;
    private final EbookViewerPreferenceRepository ebookViewerPreferencesRepository;
    private final SidecarMetadataWriter sidecarMetadataWriter;
    private final FileStreamingService fileStreamingService;
    private final AuditService auditService;

    public List<Book> getBookDTOs(boolean includeDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isAdmin = user.getPermissions().isAdmin();

        List<Book> books = isAdmin
                ? bookQueryService.getAllBooks(includeDescription)
                : bookQueryService.getAllBooksByLibraryIds(
                getUserLibraryIds(user),
                includeDescription,
                user.getId()
        );

        Set<Long> bookIds = books.stream().map(Book::getId).collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap =
                readingProgressService.fetchUserProgress(user.getId(), bookIds);
        Map<Long, UserBookFileProgressEntity> fileProgressMap =
                readingProgressService.fetchUserFileProgress(user.getId(), bookIds);

        books.forEach(book -> {
            readingProgressService.enrichBookWithProgress(
                    book,
                    progressMap.get(book.getId()),
                    fileProgressMap.get(book.getId())
            );
            Set<Shelf> filtered = filterShelvesByUserId(book.getShelves(), user.getId());
            book.setShelves(!includeDescription && filtered != null && filtered.isEmpty() ? null : filtered);
        });

        return books;
    }

    public String computeBooksETag(boolean includeDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isAdmin = user.getPermissions().isAdmin();

        Object[] row = extractStatsRow(isAdmin
                ? bookRepository.getBookStats()
                : bookRepository.getBookStatsByLibraryIds(getUserLibraryIds(user)));

        long count = ((Number) row[0]).longValue();
        long maxBookTs = toEpochMilli(row[1]);
        long maxAddedTs = toEpochMilli(row[2]);
        long maxProgressTs = toEpochMilli(userBookProgressRepository.getMaxProgressTimestamp(user.getId()));

        return "\"" + count + "-" + maxBookTs + "-" + maxAddedTs + "-" + maxProgressTs
                + "-" + includeDescription + "\"";
    }

    public BookSyncResponse getBooksDelta(Instant since, boolean includeDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isAdmin = user.getPermissions().isAdmin();
        Set<Long> libraryIds = isAdmin ? null : getUserLibraryIds(user);

        // Find book IDs modified since the given timestamp
        Set<Long> modifiedBookIds = isAdmin
                ? bookRepository.findBookIdsModifiedSince(since)
                : bookRepository.findBookIdsModifiedSinceByLibraryIds(since, libraryIds);

        // Find book IDs with progress changes
        Set<Long> progressChangedIds = userBookProgressRepository.findBookIdsWithProgressChangedSince(user.getId(), since);

        // Combine all changed IDs
        Set<Long> allChangedIds = new HashSet<>(modifiedBookIds);
        allChangedIds.addAll(progressChangedIds);

        // Find deleted book IDs
        List<Long> deletedIds = isAdmin
                ? bookRepository.findDeletedBookIdsSince(since)
                : bookRepository.findDeletedBookIdsSinceByLibraryIds(since, libraryIds);

        // Fetch and enrich changed books
        List<Book> changedBooks = List.of();
        if (!allChangedIds.isEmpty()) {
            List<BookEntity> bookEntities = bookQueryService.findAllWithMetadataByIds(allChangedIds);
            if (!isAdmin) {
                // Content restriction is handled by BookQueryService for non-admin users,
                // but since we're using findAllWithMetadataByIds directly, filter by library
                bookEntities = bookEntities.stream()
                        .filter(b -> libraryIds.contains(b.getLibrary().getId()))
                        .collect(Collectors.toList());
            }
            changedBooks = enrichBookEntities(bookEntities, user, includeDescription);
        }

        // Get total book count for client to detect full-sync needs
        Object[] row = extractStatsRow(isAdmin
                ? bookRepository.getBookStats()
                : bookRepository.getBookStatsByLibraryIds(libraryIds));
        long totalCount = ((Number) row[0]).longValue();

        return BookSyncResponse.builder()
                .books(changedBooks)
                .deletedIds(deletedIds)
                .syncTimestamp(Instant.now().toString())
                .totalBookCount(totalCount)
                .build();
    }

    private List<Book> enrichBookEntities(List<BookEntity> bookEntities, BookLoreUser user, boolean includeDescription) {
        // Map entities through the same DTO pipeline as full fetch (includes field stripping)
        List<Book> books = bookQueryService.mapEntitiesToDto(bookEntities, includeDescription, user.getId());

        Set<Long> bookIds = books.stream().map(Book::getId).collect(Collectors.toSet());
        Map<Long, UserBookProgressEntity> progressMap =
                readingProgressService.fetchUserProgress(user.getId(), bookIds);
        Map<Long, UserBookFileProgressEntity> fileProgressMap =
                readingProgressService.fetchUserFileProgress(user.getId(), bookIds);

        books.forEach(book -> {
            readingProgressService.enrichBookWithProgress(
                    book,
                    progressMap.get(book.getId()),
                    fileProgressMap.get(book.getId())
            );
            Set<Shelf> filtered = filterShelvesByUserId(book.getShelves(), user.getId());
            book.setShelves(!includeDescription && filtered != null && filtered.isEmpty() ? null : filtered);
        });

        return books;
    }

    private long toEpochMilli(Object value) {
        if (value == null) return 0;
        if (value instanceof Instant inst) return inst.toEpochMilli();
        if (value instanceof Timestamp ts) return ts.toInstant().toEpochMilli();
        return 0;
    }

    private Object[] extractStatsRow(Object[] result) {
        if (result.length > 0 && result[0] instanceof Object[]) {
            return (Object[]) result[0];
        }
        return result;
    }

    private Set<Long> getUserLibraryIds(BookLoreUser user) {
        return user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
    }

    public List<Book> getBooksByIds(Set<Long> bookIds, boolean withDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        List<BookEntity> bookEntities = bookQueryService.findAllWithMetadataByIds(bookIds);
        Set<Long> entityIds = bookEntities.stream().map(BookEntity::getId).collect(Collectors.toSet());

        Map<Long, UserBookProgressEntity> progressMap =
                readingProgressService.fetchUserProgress(user.getId(), entityIds);
        Map<Long, UserBookFileProgressEntity> fileProgressMap =
                readingProgressService.fetchUserFileProgress(user.getId(), entityIds);

        return bookEntities.stream().map(bookEntity -> {
            Book book = bookMapper.toBook(bookEntity);
            if (!withDescription) book.getMetadata().setDescription(null);
            readingProgressService.enrichBookWithProgress(
                    book,
                    progressMap.get(bookEntity.getId()),
                    fileProgressMap.get(bookEntity.getId())
            );
            return book;
        }).collect(Collectors.toList());
    }

    public Book getBook(long bookId, boolean withDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        UserBookProgressEntity userProgress = userBookProgressRepository.findByUserIdAndBookId(user.getId(), bookId)
                .orElse(new UserBookProgressEntity());

        // Fetch file-level progress for the book (most recent across all files)
        UserBookFileProgressEntity fileProgress = readingProgressService
                .fetchUserFileProgress(user.getId(), Set.of(bookId))
                .get(bookId);

        Book book = bookMapper.toBook(bookEntity);
        book.setShelves(filterShelvesByUserId(book.getShelves(), user.getId()));
        readingProgressService.enrichBookWithProgress(book, userProgress, fileProgress);

        if (!withDescription) {
            book.getMetadata().setDescription(null);
        }

        return book;
    }

    public Book getBookByHash(String md5Hash, boolean withDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        // Find book by MD5 hash
        BookEntity bookEntity = bookRepository.findByCurrentHash(md5Hash)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book with hash " + md5Hash + " not found"));

        // Check if user has access to this book (via library access)
        boolean hasAccess = bookEntity.getLibraryPath().getLibrary().getUsers().stream()
                .anyMatch(u -> u.getId().equals(user.getId()));

        if (!hasAccess) {
            throw ApiError.GENERIC_NOT_FOUND.createException("Book with hash " + md5Hash + " not found");
        }

        return getBook(bookEntity.getId(), withDescription);
    }

    public BookViewerSettings getBookViewerSetting(long bookId, long bookFileId) {
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        BookLoreUser user = authenticationService.getAuthenticatedUser();

        BookViewerSettings.BookViewerSettingsBuilder settingsBuilder = BookViewerSettings.builder();

        BookFileEntity bookFile = bookEntity.getBookFiles().stream()
                .filter(bf -> bf.getId().equals(bookFileId))
                .findFirst()
                .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("Book file not found: " + bookFileId));
        BookFileType bookType = bookFile.getBookType();
        if (bookType == BookFileType.EPUB || bookType == BookFileType.FB2
                || bookType == BookFileType.MOBI
                || bookType == BookFileType.AZW3) {
            ebookViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(epubPref -> settingsBuilder.ebookSettings(EbookViewerPreferences.builder()
                            .bookId(bookId)
                            .userId(user.getId())
                            .fontFamily(epubPref.getFontFamily())
                            .fontSize(epubPref.getFontSize())
                            .gap(epubPref.getGap())
                            .hyphenate(epubPref.getHyphenate())
                            .isDark(epubPref.getIsDark())
                            .justify(epubPref.getJustify())
                            .lineHeight(epubPref.getLineHeight())
                            .maxBlockSize(epubPref.getMaxBlockSize())
                            .maxColumnCount(epubPref.getMaxColumnCount())
                            .maxInlineSize(epubPref.getMaxInlineSize())
                            .theme(epubPref.getTheme())
                            .flow(epubPref.getFlow())
                            .build()));
        } else if (bookType == BookFileType.PDF) {
            pdfViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(pdfPref -> settingsBuilder.pdfSettings(PdfViewerPreferences.builder()
                            .bookId(bookId)
                            .zoom(pdfPref.getZoom())
                            .spread(pdfPref.getSpread())
                            .build()));
            newPdfViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(pdfPref -> settingsBuilder.newPdfSettings(NewPdfViewerPreferences.builder()
                            .bookId(bookId)
                            .pageViewMode(pdfPref.getPageViewMode())
                            .pageSpread(pdfPref.getPageSpread())
                            .fitMode(pdfPref.getFitMode())
                            .scrollMode(pdfPref.getScrollMode())
                            .backgroundColor(pdfPref.getBackgroundColor())
                            .build()));
        } else if (bookType == BookFileType.CBX) {
            cbxViewerPreferencesRepository.findByBookIdAndUserId(bookId, user.getId())
                    .ifPresent(cbxPref -> settingsBuilder.cbxSettings(CbxViewerPreferences.builder()
                            .bookId(bookId)
                            .pageViewMode(cbxPref.getPageViewMode())
                            .pageSpread(cbxPref.getPageSpread())
                            .fitMode(cbxPref.getFitMode())
                            .scrollMode(cbxPref.getScrollMode())
                            .backgroundColor(cbxPref.getBackgroundColor())
                            .build()));
        } else {
            throw ApiError.UNSUPPORTED_BOOK_TYPE.createException();
        }
        return settingsBuilder.build();
    }

    public void updateBookViewerSetting(long bookId, BookViewerSettings bookViewerSettings) {
        bookUpdateService.updateBookViewerSetting(bookId, bookViewerSettings);
    }

    @Transactional
    public void updateReadProgress(ReadProgressRequest request) {
        readingProgressService.updateReadProgress(request);
    }

    @Transactional
    public List<BookStatusUpdateResponse> updateReadStatus(List<Long> bookIds, String status) {
        return bookUpdateService.updateReadStatus(bookIds, status);
    }

    @Transactional
    public List<Book> assignShelvesToBooks(Set<Long> bookIds, Set<Long> shelfIdsToAssign, Set<Long> shelfIdsToUnassign) {
        return bookUpdateService.assignShelvesToBooks(bookIds, shelfIdsToAssign, shelfIdsToUnassign);
    }

    public Resource getBookThumbnail(long bookId) {
        Path thumbnailPath = Paths.get(fileService.getThumbnailFile(bookId));
        try {
            if (Files.exists(thumbnailPath)) {
                return new UrlResource(thumbnailPath.toUri());
            } else {
                Path defaultCover = Paths.get("static/images/missing-cover.jpg");
                return new UrlResource(defaultCover.toUri());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load book cover for bookId=" + bookId, e);
        }
    }

    public Resource getBookCover(long bookId) {
        Path coverPath = Paths.get(fileService.getCoverFile(bookId));
        try {
            if (Files.exists(coverPath)) {
                return new UrlResource(coverPath.toUri());
            } else {
                return new ClassPathResource("static/images/missing-cover.jpg");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load book cover for bookId=" + bookId, e);
        }
    }

    public Resource getBookCover(String coverHash) {
        BookEntity bookEntity = bookRepository.findByBookCoverHash(coverHash).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(coverHash));
        return getBookCover(bookEntity.getId());
    }

    public Resource getAudiobookThumbnail(long bookId) {
        Path thumbnailPath = Paths.get(fileService.getAudiobookThumbnailFile(bookId));
        try {
            if (Files.exists(thumbnailPath)) {
                return new UrlResource(thumbnailPath.toUri());
            } else {
                return new ClassPathResource("static/images/missing-cover.jpg");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load audiobook thumbnail for bookId=" + bookId, e);
        }
    }

    public Resource getAudiobookCover(long bookId) {
        Path coverPath = Paths.get(fileService.getAudiobookCoverFile(bookId));
        try {
            if (Files.exists(coverPath)) {
                return new UrlResource(coverPath.toUri());
            } else {
                return new ClassPathResource("static/images/missing-cover.jpg");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load audiobook cover for bookId=" + bookId, e);
        }
    }

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        return bookDownloadService.downloadBook(bookId);
    }

    public void downloadAllBookFiles(Long bookId, HttpServletResponse response) {
        bookDownloadService.downloadAllBookFiles(bookId, response);
    }

    public ResponseEntity<Resource> getBookContent(long bookId) {
        return getBookContent(bookId, null);
    }

    public ResponseEntity<Resource> getBookContent(long bookId, String bookType) {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        String filePath;
        if (bookType != null) {
            BookFileType requestedType = BookFileType.valueOf(bookType.toUpperCase());
            BookFileEntity bookFile = bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No file of type " + bookType + " found for book"));
            filePath = bookFile.getFullFilePath().toString();
        } else {
            filePath = FileUtils.getBookFullPath(bookEntity);
        }
        File file = new File(filePath);
        if (!file.exists()) {
            throw ApiError.FILE_NOT_FOUND.createException(filePath);
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(resource);
    }

    public void streamBookContent(long bookId, String bookType, HttpServletRequest request, HttpServletResponse response) throws IOException {
        BookEntity bookEntity = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        String filePath;
        if (bookType != null) {
            BookFileType requestedType = BookFileType.valueOf(bookType.toUpperCase());
            BookFileEntity bookFile = bookEntity.getBookFiles().stream()
                    .filter(bf -> bf.getBookType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("No file of type " + bookType + " found for book"));
            filePath = bookFile.getFullFilePath().toString();
        } else {
            filePath = FileUtils.getBookFullPath(bookEntity);
        }

        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString();
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";
        String contentType = switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "epub" -> "application/epub+zip";
            case "mobi", "azw3" -> "application/x-mobipocket-ebook";
            case "cbz" -> "application/vnd.comicbook+zip";
            case "cbr" -> "application/vnd.comicbook-rar";
            case "fb2" -> "application/x-fictionbook+xml";
            default -> "application/octet-stream";
        };

        fileStreamingService.streamWithRangeSupport(path, contentType, request, response);
    }
    @Transactional
    public ResponseEntity<BookDeletionResponse> deleteBooks(Set<Long> ids) {
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(ids);
        List<Long> failedFileDeletions = new ArrayList<>();
        for (BookEntity book : books) {
            for (BookFileEntity bookFile : book.getBookFiles()) {
                Path fullFilePath = bookFile.getFullFilePath();
                try {
                    if (Files.exists(fullFilePath)) {
                        try {
                            monitoringRegistrationService.unregisterSpecificPath(fullFilePath.getParent());
                        } catch (Exception ex) {
                            log.warn("Failed to unregister monitoring for path: {}", fullFilePath.getParent(), ex);
                        }

                        // Handle folder-based audiobooks (delete directory recursively)
                        if (bookFile.isFolderBased() && Files.isDirectory(fullFilePath)) {
                            deleteDirectoryRecursively(fullFilePath);
                            log.info("Deleted folder-based audiobook: {}", fullFilePath);
                        } else {
                            Files.delete(fullFilePath);
                            log.info("Deleted book file: {}", fullFilePath);
                        }

                        Set<Path> libraryRoots = book.getLibrary().getLibraryPaths().stream()
                                .map(LibraryPathEntity::getPath)
                                .map(Paths::get)
                                .map(Path::normalize)
                                .collect(Collectors.toSet());

                        deleteEmptyParentDirsUpToLibraryFolders(fullFilePath.getParent(), libraryRoots);

                        try {
                            sidecarMetadataWriter.deleteSidecarFiles(fullFilePath);
                        } catch (Exception e) {
                            log.warn("Failed to delete sidecar files for: {}", fullFilePath, e);
                        }
                    }
                } catch (IOException e) {
                    log.warn("Failed to delete book file: {}", fullFilePath, e);
                    failedFileDeletions.add(book.getId());
                } finally {
                    monitoringRegistrationService.registerSpecificPath(fullFilePath.getParent(), book.getLibrary().getId());
                }
            }
        }

        bookRepository.deleteAll(books);
        auditService.log(AuditAction.BOOK_DELETED, "Deleted " + ids.size() + " book(s)");
        BookDeletionResponse response = new BookDeletionResponse(ids, failedFileDeletions);
        return failedFileDeletions.isEmpty()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(HttpStatus.MULTI_STATUS).body(response);
    }

    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    public void deleteEmptyParentDirsUpToLibraryFolders(Path currentDir, Set<Path> libraryRoots) {
        Path dir = currentDir;
        Set<String> ignoredFilenames = Set.of(".DS_Store", "Thumbs.db");
        dir = dir.toAbsolutePath().normalize();

        Set<Path> normalizedRoots = new HashSet<>();
        for (Path root : libraryRoots) {
            normalizedRoots.add(root.toAbsolutePath().normalize());
        }

        while (dir != null) {
            boolean isLibraryRoot = false;
            for (Path root : normalizedRoots) {
                try {
                    if (Files.isSameFile(root, dir)) {
                        isLibraryRoot = true;
                        break;
                    }
                } catch (IOException e) {
                    log.warn("Failed to compare paths: {} and {}", root, dir);
                }
            }

            if (isLibraryRoot) {
                log.debug("Reached library root: {}. Stopping cleanup.", dir);
                break;
            }

            File[] files = dir.toFile().listFiles();
            if (files == null) {
                log.warn("Cannot read directory: {}. Stopping cleanup.", dir);
                break;
            }

            boolean hasImportantFiles = false;
            for (File file : files) {
                if (!ignoredFilenames.contains(file.getName())) {
                    hasImportantFiles = true;
                    break;
                }
            }

            if (!hasImportantFiles) {
                for (File file : files) {
                    try {
                        Files.delete(file.toPath());
                        log.info("Deleted ignored file: {}", file.getAbsolutePath());
                    } catch (IOException e) {
                        log.warn("Failed to delete ignored file: {}", file.getAbsolutePath());
                    }
                }
                try {
                    Files.delete(dir);
                    log.info("Deleted empty directory: {}", dir);
                } catch (IOException e) {
                    log.warn("Failed to delete directory: {}", dir, e);
                    break;
                }
                dir = dir.getParent();
            } else {
                log.debug("Directory {} contains important files. Stopping cleanup.", dir);
                break;
            }
        }
    }

    public Set<Shelf> filterShelvesByUserId(Set<Shelf> shelves, Long userId) {
        if (shelves == null) return Collections.emptySet();
        return shelves.stream()
                .filter(shelf -> userId.equals(shelf.getUserId()))
                .collect(Collectors.toSet());
    }

    public List<org.booklore.model.dto.response.BookSearchResult> fuzzySearch(String searchTitle, String searchIsbn) {
        if (searchTitle == null && searchIsbn == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("At least one search parameter (title or isbn) must be provided");
        }

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isAdmin = user.getPermissions().isAdmin();

        List<BookEntity> allBooks = isAdmin
                ? bookRepository.findAllWithMetadata()
                : bookRepository.findAllWithMetadataByLibraryIds(
                user.getAssignedLibraries().stream()
                        .map(Library::getId)
                        .collect(Collectors.toList())
        );

        org.apache.commons.text.similarity.FuzzyScore fuzzyScore = 
                new org.apache.commons.text.similarity.FuzzyScore(java.util.Locale.ENGLISH);

        return allBooks.stream()
                .map(book -> {
                    String bookTitle = book.getMetadata() != null && book.getMetadata().getTitle() != null
                            ? book.getMetadata().getTitle()
                            : (book.getPrimaryBookFile() != null ? book.getPrimaryBookFile().getFileName() : "");
                    
                    double normalizedScore = 0.0;
                    
                    // Search by ISBN if provided
                    if (searchIsbn != null && book.getMetadata() != null) {
                        String cleanSearchIsbn = searchIsbn.replaceAll("[^0-9X]", "");
                        String bookIsbn13 = book.getMetadata().getIsbn13() != null 
                                ? book.getMetadata().getIsbn13().replaceAll("[^0-9X]", "") 
                                : "";
                        String bookIsbn10 = book.getMetadata().getIsbn10() != null 
                                ? book.getMetadata().getIsbn10().replaceAll("[^0-9X]", "") 
                                : "";
                        
                        // Exact match gets perfect score
                        if (cleanSearchIsbn.equalsIgnoreCase(bookIsbn13) || cleanSearchIsbn.equalsIgnoreCase(bookIsbn10)) {
                            normalizedScore = 1.0;
                        } else if (!bookIsbn13.isEmpty() || !bookIsbn10.isEmpty()) {
                            // Fuzzy match for partial ISBN matches
                            int isbnScore13 = !bookIsbn13.isEmpty() 
                                    ? fuzzyScore.fuzzyScore(bookIsbn13.toLowerCase(), cleanSearchIsbn.toLowerCase()) 
                                    : 0;
                            int isbnScore10 = !bookIsbn10.isEmpty() 
                                    ? fuzzyScore.fuzzyScore(bookIsbn10.toLowerCase(), cleanSearchIsbn.toLowerCase()) 
                                    : 0;
                            int bestIsbnScore = Math.max(isbnScore13, isbnScore10);
                            int maxScore = Math.max(
                                    fuzzyScore.fuzzyScore(cleanSearchIsbn, cleanSearchIsbn),
                                    Math.max(
                                            !bookIsbn13.isEmpty() ? fuzzyScore.fuzzyScore(bookIsbn13, bookIsbn13) : 0,
                                            !bookIsbn10.isEmpty() ? fuzzyScore.fuzzyScore(bookIsbn10, bookIsbn10) : 0
                                    )
                            );
                            normalizedScore = maxScore > 0 ? (double) bestIsbnScore / maxScore : 0.0;
                        }
                    }
                    
                    // Search by title if provided and no ISBN match found
                    if (searchTitle != null && normalizedScore < 1.0) {
                        int titleScore = fuzzyScore.fuzzyScore(bookTitle.toLowerCase(), searchTitle.toLowerCase());
                        int maxScore = Math.max(
                                fuzzyScore.fuzzyScore(bookTitle, bookTitle),
                                fuzzyScore.fuzzyScore(searchTitle, searchTitle)
                        );
                        double titleNormalizedScore = maxScore > 0 ? (double) titleScore / maxScore : 0.0;
                        
                        // Use the better score between ISBN and title
                        normalizedScore = Math.max(normalizedScore, titleNormalizedScore);
                    }
                    
                    String hash = book.getPrimaryBookFile() != null 
                            ? book.getPrimaryBookFile().getCurrentHash() 
                            : null;
                    
                    String coverHash = book.getBookCoverHash();
                    
                    return org.booklore.model.dto.response.BookSearchResult.builder()
                            .id(book.getId())
                            .title(bookTitle)
                            .hash(hash)
                            .coverHash(coverHash)
                            .matchScore(normalizedScore)
                            .build();
                })
                .filter(result -> result.getMatchScore() > 0.0)
                .sorted((a, b) -> Double.compare(b.getMatchScore(), a.getMatchScore()))
                .limit(50)
                .collect(Collectors.toList());
    }

    @Deprecated
    public List<org.booklore.model.dto.response.BookSearchResult> fuzzySearchByTitle(String searchTitle) {
        return fuzzySearch(searchTitle, null);
    }

}

