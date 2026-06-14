package org.booklore.service.book;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.BookConversionRequest;
import org.booklore.model.dto.response.BookConversionCapabilityResponse;
import org.booklore.model.dto.response.BookConversionResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.file.FileFingerprint;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.restriction.ContentRestrictionService;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookConversionService {

    private static final Map<BookFileType, String> TARGET_EXTENSIONS = Map.of(
            BookFileType.EPUB, "epub",
            BookFileType.PDF, "pdf",
            BookFileType.MOBI, "mobi",
            BookFileType.AZW3, "azw3",
            BookFileType.FB2, "fb2"
    );
    private static final long EVENT_DRAIN_TIMEOUT_MS = 300;

    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final LibraryRepository libraryRepository;
    private final AuthenticationService authenticationService;
    private final ContentRestrictionService contentRestrictionService;
    private final CalibreEbookConversionService calibreEbookConversionService;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final NotificationService notificationService;
    private final BookMapper bookMapper;
    private final Executor taskExecutor;
    private final TransactionTemplate transactionTemplate;

    public BookConversionCapabilityResponse getCapability() {
        return new BookConversionCapabilityResponse(
                calibreEbookConversionService.isAvailable(),
                CalibreEbookConversionService.SUPPORTED_TARGET_FORMATS
        );
    }

    public BookConversionResponse scheduleConversions(BookConversionRequest request) {
        BookFileType targetFormat = request.getTargetFormat();
        if (!TARGET_EXTENSIONS.containsKey(targetFormat)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Unsupported target conversion format: " + targetFormat);
        }
        if (!calibreEbookConversionService.isAvailable()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Calibre ebook-convert is not available");
        }

        LinkedHashSet<Long> uniqueBookIds = new LinkedHashSet<>(request.getBookIds());
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        String username = user != null ? user.getUsername() : null;
        List<Long> bookIds = new ArrayList<>(uniqueBookIds);

        transactionTemplate.executeWithoutResult(status -> {
            List<BookEntity> books = loadBooks(uniqueBookIds);
            validateAccess(books, user);
        });

        taskExecutor.execute(() -> processConversions(bookIds, targetFormat, username));
        return new BookConversionResponse(uniqueBookIds.size(), targetFormat);
    }

    private List<BookEntity> loadBooks(LinkedHashSet<Long> bookIds) {
        List<BookEntity> books = new ArrayList<>(bookIds.size());
        for (Long bookId : bookIds) {
            books.add(bookRepository.findByIdWithBookFiles(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId)));
        }
        return books;
    }

    private void validateAccess(List<BookEntity> books, BookLoreUser user) {
        if (user == null) {
            throw ApiError.FORBIDDEN.createException("Authentication required.");
        }
        if (user.getPermissions().isAdmin()) {
            return;
        }

        Set<Long> assignedLibraryIds = user.getAssignedLibraries() == null
                ? Set.of()
                : user.getAssignedLibraries().stream()
                .map(org.booklore.model.dto.Library::getId)
                .collect(Collectors.toSet());

        boolean missingLibraryAccess = books.stream()
                .anyMatch(book -> book.getLibrary() == null || !assignedLibraryIds.contains(book.getLibrary().getId()));
        if (missingLibraryAccess) {
            throw ApiError.FORBIDDEN.createException("You are not authorized to access this book.");
        }

        Set<Long> permittedBookIds = contentRestrictionService.applyRestrictions(books, user.getId()).stream()
                .map(BookEntity::getId)
                .collect(Collectors.toSet());
        boolean restricted = books.stream().anyMatch(book -> !permittedBookIds.contains(book.getId()));
        if (restricted) {
            throw ApiError.FORBIDDEN.createException("You are not authorized to access this book.");
        }
    }

    private void processConversions(List<Long> bookIds, BookFileType targetFormat, String username) {
        sendLog(username, LogNotification.info("Started converting " + bookIds.size() + " book(s) to " + targetFormat));

        ConversionCounters counters = new ConversionCounters();
        for (Long bookId : bookIds) {
            ConversionStatus status = processBook(bookId, targetFormat, username);
            counters.record(status);
        }

        sendLog(username, LogNotification.info("Finished converting books to " + targetFormat + ": "
                + counters.converted + " converted, "
                + counters.skipped + " skipped, "
                + counters.failed + " failed"));
    }

    private ConversionStatus processBook(Long bookId, BookFileType targetFormat, String username) {
        Path tempDir = null;
        try {
            BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
            BookFileEntity source = book.getPrimaryBookFile();
            if (source == null) {
                return skip(username, "Book " + bookId + " has no primary file");
            }
            if (source.isFolderBased()) {
                return skip(username, "Book " + bookId + " uses a folder-based source file");
            }
            if (source.getBookType() == BookFileType.AUDIOBOOK) {
                return skip(username, "Book " + bookId + " is an audiobook");
            }
            if (!isTargetAllowed(book, targetFormat)) {
                return skip(username, "Target format " + targetFormat + " is not allowed in library " + book.getLibrary().getName());
            }
            if (hasBookFormat(book, targetFormat)) {
                return skip(username, "Book already has " + targetFormat);
            }

            Path libraryRoot = Path.of(book.getLibraryPath().getPath());
            Path sourcePath = FileUtils.requirePathWithinBase(source.getFullFilePath(), libraryRoot);
            if (!Files.isRegularFile(sourcePath) || !Files.isReadable(sourcePath)) {
                return fail(username, "Source file for book " + bookId + " is missing: " + sourcePath);
            }

            String targetFileName = buildTargetFileName(source.getFileName(), targetFormat);
            Path targetPath = buildTargetPath(libraryRoot, source.getFileSubPath(), targetFileName);
            if (Files.exists(targetPath)) {
                return skip(username, "Target file already exists for book " + bookId + ": " + targetPath.getFileName());
            }

            tempDir = Files.createTempDirectory("ebook-convert-");
            Path tempOutput = tempDir.resolve(targetPath.getFileName());
            calibreEbookConversionService.convert(sourcePath, tempOutput);

            String hash = FileFingerprint.generateHash(tempOutput);
            Long fileSizeKb = FileUtils.getFileSizeInKb(tempOutput);
            ConversionStatus status = moveAndPersistConvertedFile(bookId, book.getLibrary().getId(), source.getFileSubPath(), targetFormat, targetPath, tempOutput, hash, fileSizeKb, username);
            if (status == ConversionStatus.CONVERTED) {
                sendBookUpdate(bookId, username);
            }
            return status;
        } catch (Exception e) {
            log.error("Failed to convert book {} to {}: {}", bookId, targetFormat, e.getMessage(), e);
            sendLog(username, LogNotification.error("Failed to convert book " + bookId + " to " + targetFormat + ": " + e.getMessage()));
            return ConversionStatus.FAILED;
        } finally {
            if (tempDir != null) {
                try {
                    FileSystemUtils.deleteRecursively(tempDir);
                } catch (IOException e) {
                    log.warn("Failed to delete conversion temp directory {}: {}", tempDir, e.getMessage());
                }
            }
        }
    }

    private ConversionStatus moveAndPersistConvertedFile(Long bookId, Long libraryId, String fileSubPath, BookFileType targetFormat,
                                                         Path targetPath, Path tempOutput, String hash, Long fileSizeKb, String username) throws IOException {
        Set<Path> monitoredPaths = monitoringRegistrationService.getPathsForLibraries(Set.of(libraryId));
        monitoringRegistrationService.unregisterLibrary(libraryId);
        monitoringRegistrationService.waitForEventsDrainedByPaths(monitoredPaths, EVENT_DRAIN_TIMEOUT_MS);

        boolean targetMoved = false;
        try {
            if (Files.exists(targetPath)) {
                deleteTempOutput(tempOutput);
                return skip(username, "Target file already exists for book " + bookId + ": " + targetPath.getFileName());
            }
            Path targetParent = targetPath.getParent();
            if (targetParent != null) {
                Files.createDirectories(targetParent);
            }
            try {
                Files.move(tempOutput, targetPath);
                targetMoved = true;
            } catch (FileAlreadyExistsException e) {
                deleteTempOutput(tempOutput);
                return skip(username, "Target file already exists for book " + bookId + ": " + targetPath.getFileName());
            }

            PersistResult persistResult;
            try {
                persistResult = persistConvertedFile(bookId, fileSubPath, targetFormat, targetPath, hash, fileSizeKb);
            } catch (RuntimeException e) {
                deleteMovedTarget(targetPath, bookId);
                throw e;
            }

            if (persistResult == PersistResult.SKIPPED_DUPLICATE) {
                if (!deleteMovedTarget(targetPath, bookId)) {
                    return fail(username, "Failed to delete duplicate converted file for book " + bookId + ": " + targetPath);
                }
                return skip(username, "Book already has " + targetFormat);
            }
            return ConversionStatus.CONVERTED;
        } finally {
            reregisterLibrary(libraryId);
            if (!targetMoved) {
                deleteTempOutput(tempOutput);
            }
        }
    }

    private PersistResult persistConvertedFile(Long bookId, String fileSubPath, BookFileType targetFormat,
                                               Path targetPath, String hash, Long fileSizeKb) {
        return transactionTemplate.execute(status -> {
            BookEntity managedBook = bookRepository.findByIdWithBookFiles(bookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
            if (hasBookFormat(managedBook, targetFormat)) {
                return PersistResult.SKIPPED_DUPLICATE;
            }

            BookFileEntity convertedFile = BookFileEntity.builder()
                    .book(managedBook)
                    .fileName(targetPath.getFileName().toString())
                    .fileSubPath(fileSubPath)
                    .isBookFormat(true)
                    .folderBased(false)
                    .bookType(targetFormat)
                    .fileSizeKb(fileSizeKb)
                    .initialHash(hash)
                    .currentHash(hash)
                    .addedOn(Instant.now())
                    .build();
            bookAdditionalFileRepository.save(convertedFile);
            return PersistResult.SAVED;
        });
    }

    private void reregisterLibrary(Long libraryId) {
        try {
            libraryRepository.findByIdWithPaths(libraryId).ifPresent(library -> {
                for (LibraryPathEntity pathEntity : library.getLibraryPaths()) {
                    monitoringRegistrationService.registerLibraryPaths(libraryId, Path.of(pathEntity.getPath()));
                }
            });
        } catch (Exception e) {
            log.warn("Failed to re-register library {} for monitoring after ebook conversion: {}", libraryId, e.getMessage());
        }
    }

    private boolean isTargetAllowed(BookEntity book, BookFileType targetFormat) {
        List<BookFileType> allowedFormats = book.getLibrary().getAllowedFormats();
        return allowedFormats == null || allowedFormats.isEmpty() || allowedFormats.contains(targetFormat);
    }

    private boolean hasBookFormat(BookEntity book, BookFileType targetFormat) {
        return book.getBookFiles() != null && book.getBookFiles().stream()
                .anyMatch(bookFile -> bookFile.isBookFormat() && bookFile.getBookType() == targetFormat);
    }

    private Path buildTargetPath(Path libraryRoot, String fileSubPath, String targetFileName) {
        String relativePath = StringUtils.hasText(fileSubPath)
                ? Path.of(fileSubPath).resolve(targetFileName).toString()
                : targetFileName;
        return FileUtils.requirePathWithinBase(libraryRoot.resolve(relativePath), libraryRoot);
    }

    private String buildTargetFileName(String sourceFileName, BookFileType targetFormat) {
        String safeSourceFileName = Path.of(sourceFileName).getFileName().toString();
        int lastDot = safeSourceFileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? safeSourceFileName.substring(0, lastDot) : safeSourceFileName;
        return baseName + "." + TARGET_EXTENSIONS.get(targetFormat);
    }

    private ConversionStatus skip(String username, String message) {
        log.warn(message);
        sendLog(username, LogNotification.warn(message));
        return ConversionStatus.SKIPPED;
    }

    private void deleteTempOutput(Path tempOutput) {
        try {
            Files.deleteIfExists(tempOutput);
        } catch (IOException e) {
            log.warn("Failed to delete temporary conversion output {}: {}", tempOutput, e.getMessage());
        }
    }

    private ConversionStatus fail(String username, String message) {
        log.error(message);
        sendLog(username, LogNotification.error(message));
        return ConversionStatus.FAILED;
    }

    private boolean deleteMovedTarget(Path targetPath, Long bookId) {
        try {
            Files.deleteIfExists(targetPath);
            return true;
        } catch (IOException e) {
            log.error("Failed to delete converted file after database save failure for book {}: {}", bookId, targetPath, e);
            return false;
        }
    }

    private void sendBookUpdate(Long bookId, String username) {
        try {
            bookRepository.findByIdWithBookFiles(bookId).ifPresent(fresh -> sendNotification(username, Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false)));
        } catch (Exception e) {
            log.warn("Failed to send book update after conversion for book {}: {}", bookId, e.getMessage());
        }
    }

    private void sendLog(String username, LogNotification notification) {
        sendNotification(username, Topic.LOG, notification);
    }

    private void sendNotification(String username, Topic topic, Object message) {
        if (username != null) {
            notificationService.sendMessageToUser(username, topic, message);
        } else {
            notificationService.sendMessageToPermissions(topic, message, Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
        }
    }

    private enum ConversionStatus {
        CONVERTED,
        SKIPPED,
        FAILED
    }

    private enum PersistResult {
        SAVED,
        SKIPPED_DUPLICATE
    }

    private static final class ConversionCounters {
        private int converted;
        private int skipped;
        private int failed;

        private void record(ConversionStatus status) {
            switch (status) {
                case CONVERTED -> converted++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
    }
}
