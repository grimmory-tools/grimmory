package org.booklore.service.watcher;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.mapper.BookMapper;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.PermissionType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@AllArgsConstructor
public class PendingDeletionPool {

    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final NotificationService notificationService;
    private final BookMapper bookMapper;

    private final ConcurrentHashMap<Path, PendingDeletion> pendingByPath = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Path> hashIndex = new ConcurrentHashMap<>();

    public record FileSnapshot(Long bookFileId, String fileName, String fileSubPath,
                               String currentHash, boolean folderBased, BookFileType bookType) {}

    public record BookSnapshot(Long bookId, Long libraryPathId, String fileSubPath,
                                List<FileSnapshot> files) {}

    public record PendingDeletion(Path originalPath, boolean isDirectory, long libraryId,
                                   Instant timestamp, ScheduledFuture<?> timer,
                                   Map<String, Long> hashToBookFileId,
                                   Map<Long, BookSnapshot> affectedBooks) {}

    public record MatchResult(PendingDeletion pending, BookSnapshot book, FileSnapshot file) {}

    public record FolderMatchResult(PendingDeletion pending, Map<String, FileSnapshot> hashToFile) {}

    public void addFileDeletion(Path path, long libraryId, BookFileEntity bookFile, BookEntity book, ScheduledFuture<?> timer) {
        FileSnapshot fileSnapshot = new FileSnapshot(
                bookFile.getId(), bookFile.getFileName(), bookFile.getFileSubPath(),
                bookFile.getCurrentHash(), bookFile.isFolderBased(), bookFile.getBookType());

        BookSnapshot bookSnapshot = new BookSnapshot(
                book.getId(), book.getLibraryPath() != null ? book.getLibraryPath().getId() : null,
                bookFile.getFileSubPath(), List.of(fileSnapshot));

        Map<String, Long> hashMap = new ConcurrentHashMap<>();
        if (bookFile.getCurrentHash() != null) {
            hashMap.put(bookFile.getCurrentHash(), bookFile.getId());
        }

        Map<Long, BookSnapshot> affectedBooks = new ConcurrentHashMap<>();
        affectedBooks.put(book.getId(), bookSnapshot);

        PendingDeletion pending = new PendingDeletion(path, false, libraryId, Instant.now(), timer, hashMap, affectedBooks);
        pendingByPath.put(path, pending);

        if (bookFile.getCurrentHash() != null) {
            hashIndex.put(bookFile.getCurrentHash(), path);
        }

        log.debug("[POOL] Added file deletion: path='{}', hash='{}', bookId={}", path, bookFile.getCurrentHash(), book.getId());
    }

    public void addFolderDeletion(Path folderPath, long libraryId, List<BookEntity> booksWithFiles, ScheduledFuture<?> timer) {
        Map<String, Long> hashMap = new ConcurrentHashMap<>();
        Map<Long, BookSnapshot> affectedBooks = new ConcurrentHashMap<>();

        for (BookEntity book : booksWithFiles) {
            List<FileSnapshot> fileSnapshots = new ArrayList<>();
            for (BookFileEntity bf : book.getBookFiles()) {
                if (!bf.isBookFormat()) continue;
                FileSnapshot fs = new FileSnapshot(
                        bf.getId(), bf.getFileName(), bf.getFileSubPath(),
                        bf.getCurrentHash(), bf.isFolderBased(), bf.getBookType());
                fileSnapshots.add(fs);
                if (bf.getCurrentHash() != null) {
                    hashMap.put(bf.getCurrentHash(), bf.getId());
                    hashIndex.put(bf.getCurrentHash(), folderPath);
                }
            }
            if (!fileSnapshots.isEmpty()) {
                BookSnapshot bs = new BookSnapshot(
                        book.getId(), book.getLibraryPath() != null ? book.getLibraryPath().getId() : null,
                        book.getBookFiles().getFirst().getFileSubPath(), fileSnapshots);
                affectedBooks.put(book.getId(), bs);
            }
        }

        PendingDeletion pending = new PendingDeletion(folderPath, true, libraryId, Instant.now(), timer, hashMap, affectedBooks);
        pendingByPath.put(folderPath, pending);

        log.debug("[POOL] Added folder deletion: path='{}', books={}, hashes={}", folderPath, affectedBooks.size(), hashMap.size());
    }

    public Optional<MatchResult> matchByHash(String hash) {
        Path path = hashIndex.remove(hash);
        if (path == null) return Optional.empty();

        PendingDeletion pending = pendingByPath.get(path);
        if (pending == null) return Optional.empty();

        for (Map.Entry<Long, BookSnapshot> entry : pending.affectedBooks().entrySet()) {
            BookSnapshot bookSnap = entry.getValue();
            for (FileSnapshot fileSnap : bookSnap.files()) {
                if (hash.equals(fileSnap.currentHash())) {
                    pending.hashToBookFileId().remove(hash);

                    boolean allConsumed = pending.hashToBookFileId().isEmpty();
                    if (allConsumed) {
                        pending.timer().cancel(false);
                        pendingByPath.remove(path);
                        log.debug("[POOL] All files matched for path='{}', cancelled timer", path);
                    }

                    return Optional.of(new MatchResult(pending, bookSnap, fileSnap));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<FolderMatchResult> matchFolderByHashes(Map<Path, String> fileHashes) {
        if (fileHashes.isEmpty()) return Optional.empty();

        PendingDeletion bestMatch = null;
        int bestOverlap = 0;
        Map<String, FileSnapshot> bestFileMap = null;

        for (PendingDeletion pending : pendingByPath.values()) {
            if (!pending.isDirectory()) continue;

            Map<String, FileSnapshot> matchedFiles = new HashMap<>();
            int totalPendingFiles = 0;

            for (BookSnapshot bookSnap : pending.affectedBooks().values()) {
                for (FileSnapshot fileSnap : bookSnap.files()) {
                    totalPendingFiles++;
                    if (fileHashes.containsValue(fileSnap.currentHash())) {
                        matchedFiles.put(fileSnap.currentHash(), fileSnap);
                    }
                }
            }

            if (totalPendingFiles > 0) {
                double ratio = (double) matchedFiles.size() / totalPendingFiles;
                if (ratio >= 0.5 && matchedFiles.size() > bestOverlap) {
                    bestOverlap = matchedFiles.size();
                    bestMatch = pending;
                    bestFileMap = matchedFiles;
                }
            }
        }

        if (bestMatch != null) {
            bestMatch.timer().cancel(false);
            for (String hash : bestMatch.hashToBookFileId().keySet()) {
                hashIndex.remove(hash);
            }
            pendingByPath.remove(bestMatch.originalPath());
            log.debug("[POOL] Folder matched by hashes: path='{}', overlap={}", bestMatch.originalPath(), bestOverlap);
            return Optional.of(new FolderMatchResult(bestMatch, bestFileMap));
        }

        return Optional.empty();
    }

    @Transactional
    public void expireFileDeletion(Path path) {
        PendingDeletion pending = pendingByPath.remove(path);
        if (pending == null) return;

        for (String hash : pending.hashToBookFileId().keySet()) {
            hashIndex.remove(hash);
        }

        for (BookSnapshot bookSnap : pending.affectedBooks().values()) {
            Optional<BookEntity> bookOpt = bookRepository.findById(bookSnap.bookId());
            if (bookOpt.isEmpty()) continue;
            BookEntity book = bookOpt.get();

            long remainingFiles = bookFileRepository.countByBookId(book.getId());
            if (remainingFiles <= 1) {
                book.setDeleted(true);
                book.setDeletedAt(Instant.now());
                bookRepository.save(book);
                notificationService.sendMessageToPermissions(Topic.BOOKS_REMOVE, Set.of(book.getId()),
                        Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
                log.info("[EXPIRED] Book id={} marked as deleted (last file removed)", book.getId());
            } else {
                for (FileSnapshot fs : bookSnap.files()) {
                    bookFileRepository.findById(fs.bookFileId()).ifPresent(bf -> {
                        bookFileRepository.delete(bf);
                        log.info("[EXPIRED] BookFile id={} removed from book id={}", fs.bookFileId(), book.getId());
                    });
                }
                notificationService.sendMessageToPermissions(Topic.BOOK_UPDATE, Set.of(book.getId()),
                        Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
            }
        }
    }

    @Transactional
    public void expireFolderDeletion(Path path) {
        PendingDeletion pending = pendingByPath.remove(path);
        if (pending == null) return;

        for (String hash : pending.hashToBookFileId().keySet()) {
            hashIndex.remove(hash);
        }

        for (BookSnapshot bookSnap : pending.affectedBooks().values()) {
            Optional<BookEntity> bookOpt = bookRepository.findById(bookSnap.bookId());
            if (bookOpt.isEmpty()) continue;
            BookEntity book = bookOpt.get();
            book.setDeleted(true);
            book.setDeletedAt(Instant.now());
            bookRepository.save(book);
            log.info("[EXPIRED] Book id={} marked as deleted (folder deletion expired)", book.getId());
        }

        Set<Long> bookIds = pending.affectedBooks().keySet();
        if (!bookIds.isEmpty()) {
            notificationService.sendMessageToPermissions(Topic.BOOKS_REMOVE, bookIds,
                    Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
        }
    }

    @Transactional
    public void recoverBook(MatchResult match, org.booklore.model.entity.LibraryPathEntity newLibraryPath, String newFileSubPath, String newFileName, String hash) {
        BookEntity book = bookRepository.findById(match.book().bookId())
                .orElseThrow(() -> new IllegalStateException("Book not found: " + match.book().bookId()));

        BookFileEntity bookFile = bookFileRepository.findById(match.file().bookFileId())
                .orElseThrow(() -> new IllegalStateException("BookFile not found: " + match.file().bookFileId()));

        book.setLibraryPath(newLibraryPath);
        book.setDeleted(false);
        book.setDeletedAt(null);
        bookFile.setFileSubPath(newFileSubPath);
        bookFile.setFileName(newFileName);
        bookFile.setCurrentHash(hash);
        bookRepository.save(book);

        notificationService.sendMessageToPermissions(Topic.BOOK_ADD,
                bookMapper.toBookWithDescription(book, false), Set.of(PermissionType.ADMIN, PermissionType.MANAGE_LIBRARY));
        log.info("[RECOVERED] Book id={} recovered from pending deletion pool", book.getId());
    }

    public boolean cancelByPath(Path filePath) {
        PendingDeletion pending = pendingByPath.get(filePath);
        if (pending != null && !pending.isDirectory()) {
            pending.timer().cancel(false);
            for (String hash : pending.hashToBookFileId().keySet()) {
                hashIndex.remove(hash);
            }
            pendingByPath.remove(filePath);
            log.debug("[POOL] Cancelled pending deletion by path='{}'", filePath);
            return true;
        }

        for (var entry : pendingByPath.entrySet()) {
            PendingDeletion pd = entry.getValue();
            if (pd.isDirectory() && filePath.startsWith(entry.getKey())) {
                for (BookSnapshot bookSnap : pd.affectedBooks().values()) {
                    for (FileSnapshot fileSnap : bookSnap.files()) {
                        String expectedFilePath = bookSnap.fileSubPath() + "/" + fileSnap.fileName();
                        String relPath = entry.getKey().getParent() != null
                                ? entry.getKey().getParent().relativize(filePath).toString() : filePath.toString();
                        if (relPath.endsWith(fileSnap.fileName())) {
                            pd.hashToBookFileId().remove(fileSnap.currentHash());
                            hashIndex.remove(fileSnap.currentHash());
                            if (pd.hashToBookFileId().isEmpty()) {
                                pd.timer().cancel(false);
                                pendingByPath.remove(entry.getKey());
                                log.debug("[POOL] All files matched for path='{}', cancelled timer", entry.getKey());
                            }
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean hasPendingForPaths(Set<Path> paths) {
        return pendingByPath.keySet().stream()
                .anyMatch(p -> paths.stream().anyMatch(p::startsWith));
    }
}
