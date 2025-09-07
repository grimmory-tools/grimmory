package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookAdditionalFileRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.util.FileService;
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

        List<BookAdditionalFileEntity> additionalFiles = bookAdditionalFileRepository.findAllById(additionalFileIds);
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

    private boolean tryPromoteAlternativeFormatToBook(BookEntity book, List<LibraryFile> libraryFiles) {
        List<BookAdditionalFileEntity> existingAlternativeFormats = findExistingAlternativeFormats(book, libraryFiles);

        if (existingAlternativeFormats.isEmpty()) {
            return false;
        }

        BookAdditionalFileEntity promotedFormat = existingAlternativeFormats.getFirst();
        promoteAlternativeFormatToBook(book, promotedFormat);

        bookAdditionalFileRepository.delete(promotedFormat);

        log.info("Promoted alternative format {} to main book for book ID {}", promotedFormat.getFileName(), book.getId());
        return true;
    }

    private List<BookAdditionalFileEntity> findExistingAlternativeFormats(BookEntity book, List<LibraryFile> libraryFiles) {
        Set<String> currentFileNames = libraryFiles.stream()
                .map(LibraryFile::getFileName)
                .collect(Collectors.toSet());

        if (book.getAdditionalFiles() == null) {
            return Collections.emptyList();
        }

        return book.getAdditionalFiles().stream()
                .filter(additionalFile -> AdditionalFileType.ALTERNATIVE_FORMAT.equals(additionalFile.getAdditionalFileType()))
                .filter(additionalFile -> currentFileNames.contains(additionalFile.getFileName()))
                .filter(additionalFile -> BookFileExtension.fromFileName(additionalFile.getFileName()).isPresent())
                .collect(Collectors.toList());
    }

    private void promoteAlternativeFormatToBook(BookEntity book, BookAdditionalFileEntity alternativeFormat) {
        book.setFileName(alternativeFormat.getFileName());
        book.setFileSubPath(alternativeFormat.getFileSubPath());
        BookFileExtension.fromFileName(alternativeFormat.getFileName())
                .ifPresent(ext -> book.setBookType(ext.getType()));

        book.setFileSizeKb(alternativeFormat.getFileSizeKb());
        book.setCurrentHash(alternativeFormat.getCurrentHash());
        book.setInitialHash(alternativeFormat.getInitialHash());

        bookRepository.save(book);
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
