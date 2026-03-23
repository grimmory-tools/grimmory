package org.booklore.service;

import lombok.AllArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.mapper.AuthorMapper;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.repository.AuthorRepository;
import org.booklore.repository.BookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;
    private final AuthorMapper authorMapper;

    @Transactional(readOnly = true)
    public List<String> getAuthorsByBookId(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw ApiError.BOOK_NOT_FOUND.createException(bookId);
        }
        List<AuthorEntity> authorEntities = authorRepository.findAuthorsByBookId(bookId);
        return authorEntities.stream().map(authorMapper::toAuthorEntityName).toList();
    }
}


