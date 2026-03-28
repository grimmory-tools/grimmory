package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.response.AttachBookFileResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.file.FileMoveHelper;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.progress.ReadingProgressService;
import org.booklore.util.PathPatternResolver;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class BookFileAttachmentService {

    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final AuthenticationService authenticationService;
    private final ReadingProgressService readingProgressService;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final FileMoveHelper fileMoveHelper;
    private final BookMapper bookMapper;
    private final BookService bookService;
    private final EntityManager entityManager;

    @Transactional
    public AttachBookFileResponse attachBookFiles(Long targetBookId, List<Long> sourceBookIds, boolean moveFiles) {
        BookEntity targetBook = bookRepository.findByIdWithBookFiles(targetBookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(targetBookId));

        Set<Long> uniqueSourceBookIds = new LinkedHashSet<>(sourceBookIds);
        if (uniqueSourceBookIds.contains(targetBookId)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Cannot attach a book to itself");
        }

        List<BookEntity> sourceBooks = new ArrayList<>();
        for (Long sourceBookId : uniqueSourceBookIds) {
            BookEntity sourceBook = bookRepository.findByIdWithBookFiles(sourceBookId)
                    .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(sourceBookId));
            sourceBooks.add(sourceBook);
        }

        BookFileEntity targetPrimaryFile = targetBook.getBookFiles().stream()
                .filter(BookFileEntity::isBookFormat)
                .findFirst()
                .orElseThrow(() -> ApiError.GENERIC_BAD_REQUEST.createException("Target book has no primary file"));

        for (BookEntity sourceBook : sourceBooks) {
            if (!targetBook.getLibrary().getId().equals(sourceBook.getLibrary().getId())) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Source book " + sourceBook.getId() + " must be in the same library as target");
            }

            List<BookFileEntity> sourceBookFiles = sourceBook.getBookFiles().stream()
                    .filter(BookFileEntity::isBookFormat)
                    .collect(Collectors.toList());

            if (sourceBookFiles.isEmpty()) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("Source book " + sourceBook.getId() + " has no book format files to attach");
            }

            for (BookFileEntity fileToMove : sourceBookFiles) {
                if (fileToMove.isFolderBased()) {
                    throw ApiError.GENERIC_BAD_REQUEST.createException("Source book " + sourceBook.getId() + " contains a folder-based audiobook. Folder-based books cannot be attached.");
                }

                Path sourceFilePath = fileToMove.getFullFilePath();
                if (!Files.exists(sourceFilePath)) {
                    throw ApiError.GENERIC_BAD_REQUEST.createException(
                            "Source file not found at expected location: " + sourceFilePath +
                            ". The file may have been moved, deleted, or the database record is out of sync.");
                }
            }
        }

        List<Long> deletedSourceBookIds;
        if (moveFiles) {
            deletedSourceBookIds = attachWithFileMove(targetBook, sourceBooks, targetPrimaryFile);
        } else {
            deletedSourceBookIds = attachWithoutFileMove(targetBook, sourceBooks);
        }

        return new AttachBookFileResponse(getUpdatedBook(targetBookId), deletedSourceBookIds);
    }

    private List<Long> attachWithoutFileMove(BookEntity targetBook, List<BookEntity> sourceBooks) {
        List<Long> sourceBooksToDeleteIds = new ArrayList<>();
        Path targetLibraryRoot = Paths.get(targetBook.getLibraryPath().getPath()).toAbsolutePath().normalize();

        for (BookEntity sourceBook : sourceBooks) {
            boolean sameLibraryPath = sourceBook.getLibraryPath().getId().equals(targetBook.getLibraryPath().getId());
            List<BookFileEntity> bookFormatFiles = sourceBook.getBookFiles().stream()
                    .filter(BookFileEntity::isBookFormat)
                    .toList();

            if (!bookFormatFiles.isEmpty()) {
                if (sameLibraryPath) {
                    List<Long> bookFileIds = bookFormatFiles.stream()
                            .map(BookFileEntity::getId)
                            .toList();
                    bookFileRepository.reassignFilesToBook(targetBook.getId(), bookFileIds);
                } else {
                    Path sourceLibraryRoot = Paths.get(sourceBook.getLibraryPath().getPath()).toAbsolutePath().normalize();
                    for (BookFileEntity file : bookFormatFiles) {
                        Path fileDir = sourceLibraryRoot.resolve(file.getFileSubPath()).normalize();
                        String newSubPath = fileDir.equals(targetLibraryRoot)
                                ? ""
                                : targetLibraryRoot.relativize(fileDir).toString();
                        bookFileRepository.reassignFileToBookWithPath(targetBook.getId(), newSubPath, file.getId());
                    }
                }
            }

            long remainingBookFiles = sourceBook.getBookFiles().size() - bookFormatFiles.size();
            if (remainingBookFiles == 0) {
                sourceBooksToDeleteIds.add(sourceBook.getId());
            }
        }

        entityManager.flush();
        entityManager.clear();

        if (!sourceBooksToDeleteIds.isEmpty()) {
            List<BookEntity> booksToDelete = bookRepository.findAllById(sourceBooksToDeleteIds);
            bookRepository.deleteAll(booksToDelete);
        }

        return sourceBooksToDeleteIds;
    }

    private List<Long> attachWithFileMove(BookEntity targetBook, List<BookEntity> sourceBooks,
                                          BookFileEntity targetPrimaryFile) {
        String fileNamingPattern = fileMoveHelper.getFileNamingPattern(targetBook.getLibrary());
        String patternResolvedPath = PathPatternResolver.resolvePattern(targetBook, targetPrimaryFile, fileNamingPattern);
        Path libraryRootPath = Paths.get(targetBook.getLibraryPath().getPath());
        Path patternFullPath = libraryRootPath.resolve(patternResolvedPath);

        Path actualPrimaryFilePath = targetPrimaryFile.getFullFilePath();
        boolean primaryFileAtPatternLocation = Files.exists(patternFullPath) &&
                patternFullPath.normalize().equals(actualPrimaryFilePath.normalize());

        Path targetDirectory = patternFullPath.getParent();
        if (targetDirectory == null) {
            targetDirectory = libraryRootPath;
        }
        String targetFileSubPath = libraryRootPath.equals(targetDirectory)
                ? ""
                : libraryRootPath.relativize(targetDirectory).toString();

        String patternFileName = Paths.get(patternResolvedPath).getFileName().toString();
        int lastDot = patternFileName.lastIndexOf('.');
        String baseFileName = lastDot > 0 ? patternFileName.substring(0, lastDot) : patternFileName;

        if (!primaryFileAtPatternLocation && !Files.exists(actualPrimaryFilePath)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException(
                    "Target book's primary file not found at expected location: " + actualPrimaryFilePath +
                    ". Please ensure the target book's files exist before attaching files.");
        }

        Long libraryId = targetBook.getLibrary().getId();
        List<BookEntity> sourceBooksToDelete = new ArrayList<>();
        List<Path> sourceDirectoriesToCleanup = new ArrayList<>();
        Set<Path> pathsToReregister = new HashSet<>();

        try {
            try {
                monitoringRegistrationService.unregisterSpecificPath(targetDirectory);
            } catch (Exception ex) {
                log.warn("Failed to unregister target directory from monitoring: {}", targetDirectory, ex);
            }
            pathsToReregister.add(targetDirectory);

            if (!primaryFileAtPatternLocation) {
                log.info("Primary file not at pattern location, organizing target book files first");

                for (BookFileEntity existingFile : targetBook.getBookFiles()) {
                    Path currentPath = existingFile.getFullFilePath();
                    if (Files.exists(currentPath)) {
                        Path sourceDir = currentPath.getParent();
                        if (sourceDir != null) {
                            try {
                                monitoringRegistrationService.unregisterSpecificPath(sourceDir);
                            } catch (Exception ex) {
                                log.warn("Failed to unregister source directory from monitoring: {}", sourceDir, ex);
                            }
                            pathsToReregister.add(sourceDir);
                            sourceDirectoriesToCleanup.add(sourceDir);
                        }
                    }
                }

                try {
                    Files.createDirectories(targetDirectory);
                } catch (IOException e) {
                    throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to create target directory: " + e.getMessage());
                }

                for (BookFileEntity existingFile : targetBook.getBookFiles()) {
                    Path currentPath = existingFile.getFullFilePath();
                    if (!Files.exists(currentPath)) {
                        log.warn("Skipping missing file during organization: {}", currentPath);
                        continue;
                    }

                    String resolvedPath = PathPatternResolver.resolvePattern(targetBook, existingFile, fileNamingPattern);
                    String newFileName = Paths.get(resolvedPath).getFileName().toString();
                    newFileName = resolveFilenameConflict(targetDirectory, newFileName);
                    Path destinationPath = targetDirectory.resolve(newFileName);

                    if (!currentPath.normalize().equals(destinationPath.normalize())) {
                        try {
                            Files.move(currentPath, destinationPath);
                            log.info("Organized file from {} to {}", currentPath, destinationPath);
                            existingFile.setFileSubPath(targetFileSubPath);
                            existingFile.setFileName(newFileName);
                        } catch (IOException e) {
                            throw ApiError.INTERNAL_SERVER_ERROR.createException(
                                    "Failed to organize file " + currentPath + ": " + e.getMessage());
                        }
                    }
                }
            }

            Map<String, Integer> extensionCounts = new HashMap<>();
            for (BookFileEntity existingFile : targetBook.getBookFiles()) {
                if (existingFile.isBookFormat()) {
                    String ext = getFileExtension(existingFile.getFileName()).toLowerCase();
                    extensionCounts.merge(ext, 1, Integer::sum);
                }
            }

            for (BookEntity sourceBook : sourceBooks) {
                List<BookFileEntity> filesToMove = sourceBook.getBookFiles().stream()
                        .filter(BookFileEntity::isBookFormat)
                        .toList();

                Set<Path> sourceDirectories = new HashSet<>();

                for (BookFileEntity fileToMove : filesToMove) {
                    Path sourceFilePath = fileToMove.getFullFilePath();
                    Path sourceDirectory = sourceFilePath.getParent();

                    if (sourceDirectory != null && sourceDirectories.add(sourceDirectory)) {
                        try {
                            monitoringRegistrationService.unregisterSpecificPath(sourceDirectory);
                        } catch (Exception ex) {
                            log.warn("Failed to unregister source directory from monitoring: {}", sourceDirectory, ex);
                        }
                        pathsToReregister.add(sourceDirectory);
                    }

                    String extension = getFileExtension(fileToMove.getFileName()).toLowerCase();
                    int existingCount = extensionCounts.getOrDefault(extension, 0);
                    String newFileName;
                    if (extension.isEmpty()) {
                        newFileName = existingCount > 0
                                ? baseFileName + "_" + existingCount
                                : baseFileName;
                    } else {
                        newFileName = existingCount > 0
                                ? baseFileName + "_" + existingCount + "." + extension
                                : baseFileName + "." + extension;
                    }
                    extensionCounts.merge(extension, 1, Integer::sum);

                    newFileName = resolveFilenameConflict(targetDirectory, newFileName);

                    Path destinationPath = targetDirectory.resolve(newFileName);
                    try {
                        Files.move(sourceFilePath, destinationPath);
                        log.info("Moved file from {} to {}", sourceFilePath, destinationPath);
                    } catch (IOException e) {
                        log.error("Failed to move file from {} to {}", sourceFilePath, destinationPath, e);
                        throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to move file: " + e.getMessage());
                    }

                    fileToMove.setFileSubPath(targetFileSubPath);
                    fileToMove.setFileName(newFileName);

                    sourceBook.getBookFiles().remove(fileToMove);
                    fileToMove.setBook(targetBook);
                    targetBook.getBookFiles().add(fileToMove);
                }

                sourceDirectoriesToCleanup.addAll(sourceDirectories);

                long remainingBookFiles = sourceBook.getBookFiles().stream()
                        .filter(BookFileEntity::isBookFormat)
                        .count();
                if (remainingBookFiles == 0) {
                    sourceBooksToDelete.add(sourceBook);
                }
            }

            List<Long> deletedSourceBookIds = sourceBooksToDelete.stream()
                    .map(BookEntity::getId)
                    .toList();

            if (!sourceBooksToDelete.isEmpty()) {
                bookRepository.deleteAll(sourceBooksToDelete);
            }

            Set<Path> libraryRoots = targetBook.getLibrary().getLibraryPaths().stream()
                    .map(LibraryPathEntity::getPath)
                    .map(Paths::get)
                    .map(Path::normalize)
                    .collect(Collectors.toSet());

            for (Path sourceDir : sourceDirectoriesToCleanup) {
                bookService.deleteEmptyParentDirsUpToLibraryFolders(sourceDir, libraryRoots);
            }

            return deletedSourceBookIds;
        } finally {
            for (Path path : pathsToReregister) {
                if (Files.exists(path) && Files.isDirectory(path)) {
                    try {
                        monitoringRegistrationService.registerSpecificPath(path, libraryId);
                    } catch (Exception ex) {
                        log.warn("Failed to re-register path for monitoring: {}", path, ex);
                    }
                }
            }
        }
    }

    private Book getUpdatedBook(Long bookId) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        BookEntity refreshedTarget = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        UserBookProgressEntity userProgress = userBookProgressRepository.findByUserIdAndBookId(user.getId(), bookId)
                .orElse(new UserBookProgressEntity());
        UserBookFileProgressEntity fileProgress = readingProgressService
                .fetchUserFileProgress(user.getId(), Set.of(bookId))
                .get(bookId);

        Book book = bookMapper.toBook(refreshedTarget);
        book.setShelves(bookService.filterShelvesByUserId(book.getShelves(), user.getId()));
        readingProgressService.enrichBookWithProgress(book, userProgress, fileProgress);

        return book;
    }

    private String resolveFilenameConflict(Path targetDirectory, String originalFileName) {
        Path targetPath = targetDirectory.resolve(originalFileName);
        if (!Files.exists(targetPath)) {
            return originalFileName;
        }

        String baseName;
        String extension;
        int lastDotIndex = originalFileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            baseName = originalFileName.substring(0, lastDotIndex);
            extension = originalFileName.substring(lastDotIndex);
        } else {
            baseName = originalFileName;
            extension = "";
        }

        int counter = 1;
        String newFileName;
        do {
            newFileName = baseName + "_" + counter + extension;
            targetPath = targetDirectory.resolve(newFileName);
            counter++;
        } while (Files.exists(targetPath) && counter < 1000);

        if (counter >= 1000) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Could not resolve filename conflict for: " + originalFileName);
        }

        return newFileName;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
