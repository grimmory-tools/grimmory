package com.adityachandel.booklore.service.book;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.service.file.FileFingerprint;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class BookCreatorService {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final BookMetadataRepository bookMetadataRepository;

    public BookEntity createShellBook(LibraryFile libraryFile, BookFileType bookFileType) {
        Optional<BookEntity> existingBookOpt = bookRepository.findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
                libraryFile.getLibraryEntity().getId(),
                libraryFile.getLibraryPathEntity().getId(),
                libraryFile.getFileSubPath(),
                libraryFile.getFileName());

        if (existingBookOpt.isPresent()) {
            log.warn("Book already exists for file: {}", libraryFile.getFileName());
            String newHash = FileFingerprint.generateHash(libraryFile.getFullPath());
            long fileSizeKb = FileUtils.getFileSizeInKb(libraryFile.getFullPath());
            BookEntity existingBook = existingBookOpt.get();
            existingBook.setCurrentHash(newHash);
            existingBook.setInitialHash(newHash);
            existingBook.setDeleted(false);
            existingBook.setFileSizeKb(fileSizeKb);
            return existingBook;
        }

        long fileSizeKb = FileUtils.getFileSizeInKb(libraryFile.getFullPath());

        BookEntity bookEntity = BookEntity.builder()
                .library(libraryFile.getLibraryEntity())
                .libraryPath(libraryFile.getLibraryPathEntity())
                .fileName(libraryFile.getFileName())
                .fileSubPath(libraryFile.getFileSubPath())
                .bookType(bookFileType)
                .fileSizeKb(fileSizeKb)
                .addedOn(Instant.now())
                .build();

        BookMetadataEntity metadata = BookMetadataEntity.builder()
                .book(bookEntity)
                .build();
        bookEntity.setMetadata(metadata);

        return bookRepository.saveAndFlush(bookEntity);
    }

    public void addCategoriesToBook(Set<String> categories, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getCategories() == null) {
            bookEntity.getMetadata().setCategories(new HashSet<>());
        }
        categories.stream()
                .map(cat -> truncate(cat, 255))
                .map(truncated -> categoryRepository.findByName(truncated)
                        .orElseGet(() -> categoryRepository.save(CategoryEntity.builder().name(truncated).build())))
                .forEach(catEntity -> bookEntity.getMetadata().getCategories().add(catEntity));
    }

    public void addAuthorsToBook(Set<String> authors, BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() == null) {
            bookEntity.getMetadata().setAuthors(new HashSet<>());
        }
        authors.stream()
                .map(authorName -> truncate(authorName, 255))
                .map(authorName -> authorRepository.findByName(authorName)
                        .orElseGet(() -> authorRepository.save(AuthorEntity.builder().name(authorName).build())))
                .forEach(authorEntity -> bookEntity.getMetadata().getAuthors().add(authorEntity));
        bookEntity.getMetadata().updateSearchText(); // Manually trigger search text update since collection modification doesn't trigger @PreUpdate
    }

    private String truncate(String input, int maxLength) {
        if (input == null)
            return null;
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }

    public void saveConnections(BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() != null && !bookEntity.getMetadata().getAuthors().isEmpty()) {
            authorRepository.saveAll(bookEntity.getMetadata().getAuthors());
        }
        bookRepository.save(bookEntity);
        bookMetadataRepository.save(bookEntity.getMetadata());
    }
}