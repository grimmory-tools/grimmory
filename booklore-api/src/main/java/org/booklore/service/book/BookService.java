package org.booklore.service.book;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.*;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.model.dto.response.BookDeletionResponse;
import org.booklore.model.dto.response.BookStatusUpdateResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.*;
import org.booklore.service.FileStreamingService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.progress.ReadingProgressService;
import org.booklore.util.FileService;
import org.booklore.util.FileUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
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
import java.util.*;
import java.util.stream.Collectors;

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


    @Transactional(readOnly = true)
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

    private Set<Long> getUserLibraryIds(BookLoreUser user) {
        return user.getAssignedLibraries().stream()
                .map(Library::getId)
                .collect(Collectors.toSet());
    }
    @Transactional(readOnly = true)
    public List<Book> getBooksByIds(Set<Long> bookIds, boolean withDescription) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        boolean isAdmin = user.getPermissions().isAdmin();

        List<BookEntity> bookEntities = bookQueryService.findAllWithMetadataByIds(bookIds);

        if (!isAdmin) {
            Set<Long> userLibraryIds = getUserLibraryIds(user);
            bookEntities = bookEntities.stream()
                    .filter(book -> userLibraryIds.contains(book.getLibrary().getId()))
                    .toList();
        }

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

    @Transactional(readOnly = true)
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


    @Transactional(readOnly = true)
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
                return new ClassPathResource("static/images/missing-cover.jpg");
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
                return getMissingCoverResource();
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
                return getMissingCoverResource();
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
                return getMissingCoverResource();
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load audiobook cover for bookId=" + bookId, e);
        }
    }

    private Resource getMissingCoverResource() {
        try {
            byte[] bytes = new ClassPathResource("static/images/missing-cover.jpg").getInputStream().readAllBytes();
            return new ByteArrayResource(bytes);
        } catch (IOException e) {
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to load missing cover image");
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
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
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
        BookEntity bookEntity = bookRepository.findByIdWithBookFiles(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
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
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        List<BookEntity> books = bookQueryService.findAllWithMetadataByIds(ids);

        if (!user.getPermissions().isAdmin()) {
            Set<Long> userLibraryIds = getUserLibraryIds(user);
            books = books.stream()
                    .filter(book -> userLibraryIds.contains(book.getLibrary().getId()))
                    .toList();
        }
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

        bookRepository.deleteAllInBatch(books);
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

}
