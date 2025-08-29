package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookNoteMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.BookNote;
import com.adityachandel.booklore.model.dto.CreateBookNoteRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.BookNoteEntity;
import com.adityachandel.booklore.repository.BookNoteRepository;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class BookNoteService {

    private final BookNoteRepository bookNoteRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BookNoteMapper mapper;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public List<BookNote> getNotesForBook(Long bookId) {
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        return bookNoteRepository.findByBookIdAndUserIdOrderByUpdatedAtDesc(bookId, currentUser.getId())
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public BookNote createOrUpdateNote(CreateBookNoteRequest request) {
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();

        BookEntity book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(request.getBookId()));

        BookLoreUserEntity user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + currentUser.getId()));

        BookNoteEntity noteEntity;

        if (request.getId() != null) {
            noteEntity = bookNoteRepository.findByIdAndUserId(request.getId(), currentUser.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Note not found: " + request.getId()));
            noteEntity.setTitle(request.getTitle());
            noteEntity.setContent(request.getContent());
        } else {
            noteEntity = BookNoteEntity.builder()
                    .user(user)
                    .book(book)
                    .title(request.getTitle())
                    .content(request.getContent())
                    .build();
        }

        BookNoteEntity savedNote = bookNoteRepository.save(noteEntity);
        return mapper.toDto(savedNote);
    }

    @Transactional
    public void deleteNote(Long noteId) {
        BookLoreUser currentUser = authenticationService.getAuthenticatedUser();
        BookNoteEntity note = bookNoteRepository.findByIdAndUserId(noteId, currentUser.getId()).orElseThrow(() -> new EntityNotFoundException("Note not found: " + noteId));
        bookNoteRepository.delete(note);
    }
}