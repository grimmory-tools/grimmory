package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.repository.*;
import com.adityachandel.booklore.util.FileUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class BookCreatorService {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final BookMetadataRepository bookMetadataRepository;

    public BookEntity createShellBook(LibraryFile libraryFile, BookFileType bookFileType) {
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
        BookMetadataEntity bookMetadataEntity = BookMetadataEntity.builder().build();
        bookEntity.setMetadata(bookMetadataEntity);
        return bookRepository.saveAndFlush(bookEntity);
    }

    public void addCategoriesToBook(List<String> categories, BookEntity bookEntity) {
        for (String category : categories) {
            Optional<CategoryEntity> catOpt = categoryRepository.findByName(category);
            CategoryEntity categoryEntity;
            if (catOpt.isPresent()) {
                categoryEntity = catOpt.get();
            } else {
                categoryEntity = CategoryEntity.builder()
                        .name(category)
                        .build();
                categoryEntity = categoryRepository.save(categoryEntity);
            }
            if (bookEntity.getMetadata().getCategories() == null) {
                bookEntity.getMetadata().setCategories(new ArrayList<>());
            }
            bookEntity.getMetadata().getCategories().add(categoryEntity);
        }
    }

    public void addAuthorsToBook(Set<String> authors, BookEntity bookEntity) {
        for (String authorStr : authors) {
            Optional<AuthorEntity> authorOptional = authorRepository.findByName(authorStr);
            AuthorEntity authorEntity;
            if (authorOptional.isPresent()) {
                authorEntity = authorOptional.get();
            } else {
                authorEntity = AuthorEntity.builder()
                        .name(authorStr)
                        .build();
                authorEntity = authorRepository.save(authorEntity);
            }
            if (bookEntity.getMetadata().getAuthors() == null) {
                bookEntity.getMetadata().setAuthors(new ArrayList<>());
            }
            bookEntity.getMetadata().getAuthors().add(authorEntity);
        }
    }

    public void saveConnections(BookEntity bookEntity) {
        if (bookEntity.getMetadata().getAuthors() != null && !bookEntity.getMetadata().getAuthors().isEmpty()) {
            authorRepository.saveAll(bookEntity.getMetadata().getAuthors());
        }
        bookRepository.save(bookEntity);
        bookMetadataRepository.save(bookEntity.getMetadata());
    }
}
