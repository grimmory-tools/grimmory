package org.grimmory.service.watcher;

import org.grimmory.exception.ApiError;
import org.grimmory.mapper.BookMapper;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.model.entity.LibraryPathEntity;
import org.grimmory.model.enums.PermissionType;
import org.grimmory.model.websocket.Topic;
import org.grimmory.repository.BookFileRepository;
import org.grimmory.repository.BookRepository;
import org.grimmory.service.NotificationService;
import org.grimmory.util.FileUtils;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

import static org.booklore.model.enums.PermissionType.ADMIN;
import static org.booklore.model.enums.PermissionType.MANAGE_LIBRARY;

@Slf4j
@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class BookFilePersistenceService {

    private final EntityManager entityManager;
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final NotificationService notificationService;
    private final BookMapper bookMapper;

    @Transactional
    public void updatePathIfChanged(BookEntity book, LibraryEntity libraryEntity, Path path, String currentHash) {
        LibraryPathEntity newLibraryPath = getLibraryPathEntityForFile(libraryEntity, path.toString());
        newLibraryPath = entityManager.merge(newLibraryPath);

        String newSubPath = FileUtils.getRelativeSubPath(newLibraryPath.getPath(), path);
        String newFileName = path.getFileName().toString();

        BookFileEntity matchedFile = book.getBookFiles().stream()
                .filter(bf -> currentHash.equals(bf.getCurrentHash()))
                .findFirst()
                .orElse(book.getPrimaryBookFile());

        boolean changed = !Objects.equals(newSubPath, matchedFile.getFileSubPath())
                || !Objects.equals(newFileName, matchedFile.getFileName())
                || !Objects.equals(newLibraryPath.getId(), book.getLibraryPath().getId())
                || Boolean.TRUE.equals(book.getDeleted());

        if (changed) {
            book.setLibraryPath(newLibraryPath);
            matchedFile.setFileSubPath(newSubPath);
            matchedFile.setFileName(newFileName);
            book.setDeleted(Boolean.FALSE);
            book.setDeletedAt(null);
            bookRepository.save(book);
            log.info("[FILE_CREATE] Updated path / undeleted existing book with hash '{}': '{}'", currentHash, path);
        } else {
            log.info("[FILE_CREATE] Book with hash '{}' already exists at same path. Skipping update.", currentHash);
        }
        notificationService.sendMessageToPermissions(Topic.BOOK_ADD, bookMapper.toBookWithDescription(book, false), Set.of(ADMIN, MANAGE_LIBRARY));
    }

    String findMatchingLibraryPath(LibraryEntity libraryEntity, Path filePath) {
        return libraryEntity.getLibraryPaths().stream()
                .map(lp -> Paths.get(lp.getPath()).toAbsolutePath().normalize())
                .filter(base -> filePath.toAbsolutePath().normalize().startsWith(base))
                .map(Path::toString)
                .findFirst()
                .orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException("No matching libraryPath for: " + filePath));
    }

    LibraryPathEntity getLibraryPathEntityForFile(LibraryEntity libraryEntity, String inputPath) {
        Path fullPath = Paths.get(inputPath).toAbsolutePath().normalize();
        return libraryEntity.getLibraryPaths().stream()
                .map(lp -> Map.entry(lp, Paths.get(lp.getPath()).toAbsolutePath().normalize()))
                .filter(entry -> fullPath.startsWith(entry.getValue()))
                .max(Comparator.comparingInt(entry -> entry.getValue().getNameCount()))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(inputPath));
    }

    @Transactional
    public int markAllBooksUnderPathAsDeleted(long libraryPathId, String relativeFolderPath) {
        if (relativeFolderPath == null) {
            throw new IllegalArgumentException("relativeFolderPath cannot be null");
        }
        List<BookEntity> books = bookRepository.findAllByLibraryPathIdAndFileSubPathStartingWith(libraryPathId, relativeFolderPath);
        books.forEach(book -> {
            book.setDeleted(true);
            book.setDeletedAt(Instant.now());
        });

        bookRepository.saveAll(books);
        return books.size();
    }

    public Optional<BookEntity> findByLibraryPathSubPathAndFileName(long libraryPathId, String fileSubPath, String fileName) {
        return bookRepository.findByLibraryPath_IdAndFileSubPathAndFileName(libraryPathId, fileSubPath, fileName);
    }

    public Optional<BookFileEntity> findBookFileByLibraryPathSubPathAndFileName(long libraryPathId, String fileSubPath, String fileName) {
        return bookFileRepository.findByLibraryPathIdAndFileSubPathAndFileName(libraryPathId, fileSubPath, fileName);
    }

    @Transactional
    public void deleteBookFile(BookFileEntity bookFile) {
        bookFileRepository.delete(bookFile);
    }

    @Transactional
    public void markBookAsDeleted(BookEntity book) {
        book.setDeleted(true);
        book.setDeletedAt(Instant.now());
        bookRepository.save(book);
    }

    @Transactional
    public void save(BookEntity book) {
        bookRepository.save(book);
    }

    public long countBookFilesByBookId(Long bookId) {
        return bookFileRepository.countByBookId(bookId);
    }

    @Transactional
    public void recoverFolderBook(PendingDeletionPool.BookSnapshot bookSnap, LibraryPathEntity newLibraryPath,
                                   Path folderPath, Map<Path, String> fileHashes) {
        BookEntity book = bookRepository.findById(bookSnap.bookId()).orElse(null);
        if (book == null) return;

        book.setLibraryPath(entityManager.merge(newLibraryPath));
        book.setDeleted(false);
        book.setDeletedAt(null);

        for (PendingDeletionPool.FileSnapshot fs : bookSnap.files()) {
            bookFileRepository.findById(fs.bookFileId()).ifPresent(bf -> {
                for (var entry : fileHashes.entrySet()) {
                    if (entry.getValue().equals(fs.currentHash())) {
                        Path filePath = entry.getKey();
                        String newSubPath = FileUtils.getRelativeSubPath(newLibraryPath.getPath(), filePath);
                        bf.setFileSubPath(newSubPath);
                        bf.setFileName(filePath.getFileName().toString());
                        bf.setCurrentHash(entry.getValue());
                        break;
                    }
                }
            });
        }

        bookRepository.save(book);
        notificationService.sendMessageToPermissions(Topic.BOOK_ADD,
                bookMapper.toBookWithDescription(book, false), Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
        log.info("[RECOVERED] Book id={} recovered from folder move", book.getId());
    }
}
