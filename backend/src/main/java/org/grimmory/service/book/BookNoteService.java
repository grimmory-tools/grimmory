package org.grimmory.service.book;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.exception.ApiError;
import org.grimmory.mapper.BookNoteMapper;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.BookNote;
import org.grimmory.model.dto.CreateBookNoteRequest;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookLoreUserEntity;
import org.grimmory.model.entity.BookNoteEntity;
import org.grimmory.repository.BookNoteRepository;
import org.grimmory.repository.BookRepository;
import org.grimmory.repository.UserRepository;
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