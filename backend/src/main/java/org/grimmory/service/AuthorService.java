package org.grimmory.service;

import lombok.AllArgsConstructor;
import org.grimmory.exception.ApiError;
import org.grimmory.mapper.AuthorMapper;
import org.grimmory.model.entity.AuthorEntity;
import org.grimmory.repository.AuthorRepository;
import org.grimmory.repository.BookRepository;
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


