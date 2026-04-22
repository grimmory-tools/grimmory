package org.booklore.service.library;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.service.NotificationService;
import org.booklore.util.FileService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookDeletionService {

    private final BookRepository bookRepository;
    private final BookAdditionalFileRepository bookAdditionalFileRepository;
    private final FileService fileService;
    private final NotificationService notificationService;

    @PersistenceContext
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteRemovedAdditionalFiles(List<Long> additionalFileIds) {
        if (additionalFileIds.isEmpty()) {
            return;
        }

        List<BookFileEntity> additionalFiles = bookAdditionalFileRepository.findAllById(additionalFileIds);
        bookAdditionalFileRepository.deleteAll(additionalFiles);
        entityManager.flush();
        entityManager.clear();

        log.info("Deleted {} additional files from database", additionalFileIds.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processDeletedLibraryFiles(List<Long> deletedBookIds, List<LibraryFile> libraryFiles) {
        if (deletedBookIds.isEmpty()) {
            return;
        }

        List<BookEntity> books = bookRepository.findAllById(deletedBookIds);
        List<Long> booksToDelete = new ArrayList<>();

        for (BookEntity book : books) {
            if (!tryPromoteAlternativeFormatToBook(book, libraryFiles)) {
                booksToDelete.add(book.getId());
            }
        }

        entityManager.flush();
        entityManager.clear();

        if (!booksToDelete.isEmpty()) {
            deleteRemovedBooks(booksToDelete);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteRemovedBooks(List<Long> bookIds) {
        List<BookEntity> books = bookRepository.findAllById(bookIds);
        for (BookEntity book : books) {
            try {
                deleteDirectoryRecursively(Path.of(fileService.getImagesFolder(book.getId())));
                Path backupDir = Path.of(fileService.getBookMetadataBackupPath(book.getId()));
                if (Files.exists(backupDir)) {
                    deleteDirectoryRecursively(backupDir);
                }
            } catch (Exception e) {
                log.warn("Failed to clean up files for book ID {}: {}", book.getId(), e.getMessage());
            }
        }
        bookRepository.deleteAll(books);
        entityManager.flush();
        entityManager.clear();
        notificationService.sendMessage(Topic.BOOKS_REMOVE, bookIds);
        if (bookIds.size() > 1) log.info("Books removed: {}", bookIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeDisallowedFormats(LibraryEntity libraryEntity) {
        List<BookFileType> allowedFormats = libraryEntity.getAllowedFormats();
        if (allowedFormats == null || allowedFormats.isEmpty()) {
            return;
        }

        Set<BookFileType> allowed = EnumSet.copyOf(allowedFormats);
        List<BookFileEntity> allBookFiles = bookAdditionalFileRepository.findByLibraryId(libraryEntity.getId());

        List<BookFileEntity> disallowedFiles = allBookFiles.stream()
                .filter(BookFileEntity::isBook)
                .filter(bf -> !allowed.contains(bf.getBookType()))
                .toList();

        if (disallowedFiles.isEmpty()) {
            return;
        }

        Set<Long> affectedBookIds = disallowedFiles.stream()
                .map(bf -> bf.getBook().getId())
                .collect(Collectors.toSet());

        Map<Long, Long> remainingCounts = allBookFiles.stream()
                .filter(BookFileEntity::isBook)
                .filter(bf -> allowed.contains(bf.getBookType()))
                .filter(bf -> affectedBookIds.contains(bf.getBook().getId()))
                .collect(Collectors.groupingBy(bf -> bf.getBook().getId(), Collectors.counting()));

        List<Long> booksToDelete = affectedBookIds.stream()
                .filter(bookId -> remainingCounts.getOrDefault(bookId, 0L) == 0)
                .toList();

        bookAdditionalFileRepository.deleteAll(disallowedFiles);
        entityManager.flush();
        entityManager.clear();

        if (!booksToDelete.isEmpty()) {
            log.info("Deleting {} books with no remaining files of allowed formats in library: {}",
                    booksToDelete.size(), libraryEntity.getName());
            deleteRemovedBooks(booksToDelete);
        }

        log.info("Purged {} book files of disallowed formats from library: {}",
                disallowedFiles.size(), libraryEntity.getName());
    }

    private boolean tryPromoteAlternativeFormatToBook(BookEntity book, List<LibraryFile> libraryFiles) {
        Set<String> existingFileNames = libraryFiles.stream()
                .map(LibraryFile::getFileName)
                .collect(Collectors.toSet());

        List<BookFileEntity> deletedBookFiles = book.getBookFiles().stream()
                .filter(BookFileEntity::isBook)
                .filter(bf -> !existingFileNames.contains(bf.getFileName()))
                .toList();

        deletedBookFiles.forEach(bf -> {
            book.getBookFiles().remove(bf);
            bookAdditionalFileRepository.delete(bf);
        });

        boolean hasRemainingBookFiles = book.getBookFiles().stream()
                .anyMatch(BookFileEntity::isBook);

        if (hasRemainingBookFiles) {
            bookRepository.save(book);
            log.info("Removed {} deleted book file(s) for book ID {}", deletedBookFiles.size(), book.getId());
            return true;
        }
        return false;
    }


    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Failed to delete file or directory: {}", p, e);
                    }
                });
            }
        }
    }
}
