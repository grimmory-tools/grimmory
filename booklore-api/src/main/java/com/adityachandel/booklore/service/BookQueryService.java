package com.adityachandel.booklore.service;

import com.adityachandel.booklore.mapper.v2.BookMapperV2;
import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookQueryService {

    private final BookRepository bookRepository;
    private final BookMapperV2 bookMapperV2;

    public List<Book> getAllBooks(boolean includeDescription) {
        List<BookEntity> books = bookRepository.findAllWithMetadata();
        return books.stream()
                .map(book -> {
                    Book dto = bookMapperV2.toDTO(book);
                    if (!includeDescription && dto.getMetadata() != null) {
                        dto.getMetadata().setDescription(null);
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<Book> getAllBooksByLibraryIds(Set<Long> libraryIds, boolean includeDescription) {
        List<BookEntity> books = bookRepository.findAllWithMetadataByLibraryIds(libraryIds);
        return books.stream()
                .map(book -> {
                    Book dto = bookMapperV2.toDTO(book);
                    if (!includeDescription && dto.getMetadata() != null) {
                        dto.getMetadata().setDescription(null);
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<BookEntity> findAllWithMetadataByIds(Set<Long> bookIds) {
        return bookRepository.findAllWithMetadataByIds(bookIds);
    }

    public List<BookEntity> getAllFullBookEntities() {
        return bookRepository.findAllFullBooks();
    }

    public List<Book> searchBooksByMetadata(String text) {
        List<BookEntity> bookEntities = bookRepository.searchByMetadata(text);
        return bookEntities.stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
    }

    public List<Book> searchBooksByMetadataInLibraries(String text, Set<Long> libraryIds) {
        List<BookEntity> bookEntities = bookRepository.searchByMetadataAndLibraryIds(text, libraryIds);
        return bookEntities.stream()
                .map(bookMapperV2::toDTO)
                .collect(Collectors.toList());
    }

    public void saveAll(List<BookEntity> books) {
        bookRepository.saveAll(books);
    }
}
