package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.AnnotationMapper;
import org.booklore.model.dto.Annotation;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.CreateAnnotationRequest;
import org.booklore.model.dto.UpdateAnnotationRequest;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import org.booklore.service.book.AnnotationService;
import org.booklore.service.koreader.AnnotationSidecarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnnotationServiceTest {

    @Mock AnnotationRepository annotationRepository;
    @Mock BookRepository bookRepository;
    @Mock UserRepository userRepository;
    @Mock AuthenticationService authenticationService;
    @Mock AnnotationMapper mapper;
    @Mock AnnotationSidecarService annotationSidecarService;

    AnnotationService service;

    BookEntity book;
    BookLoreUserEntity userEntity;

    @BeforeEach
    void setUp() {
        service = new AnnotationService(annotationRepository, bookRepository, userRepository,
                authenticationService, mapper, annotationSidecarService);

        book = mock(BookEntity.class);
        when(book.getId()).thenReturn(10L);

        userEntity = mock(BookLoreUserEntity.class);
        when(userEntity.getId()).thenReturn(1L);

        BookLoreUser currentUser = BookLoreUser.builder().id(1L).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(currentUser);

        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(anyLong(), anyLong()))
                .thenReturn(List.of());
        when(mapper.toDto(any(AnnotationEntity.class))).thenReturn(new Annotation());
    }

    @Test
    void createAnnotation_callsWriteSidecar() {
        when(annotationRepository.existsByCfiAndBookIdAndUserId(any(), anyLong(), anyLong()))
                .thenReturn(false);

        AnnotationEntity saved = mock(AnnotationEntity.class);
        when(saved.getBook()).thenReturn(book);
        when(saved.getUser()).thenReturn(userEntity);
        when(annotationRepository.save(any())).thenReturn(saved);

        CreateAnnotationRequest req = new CreateAnnotationRequest();
        req.setCfi("epubcfi(/6/2!/4/2)");
        req.setText("selected text");
        req.setBookId(10L);

        service.createAnnotation(req);

        verify(annotationSidecarService).writeSidecar(eq(book), eq(userEntity), anyList());
    }

    @Test
    void updateAnnotation_callsWriteSidecar() {
        AnnotationEntity existing = mock(AnnotationEntity.class);
        when(existing.getBook()).thenReturn(book);
        when(existing.getUser()).thenReturn(userEntity);
        when(annotationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(existing));
        when(annotationRepository.save(any())).thenReturn(existing);

        service.updateAnnotation(99L, new UpdateAnnotationRequest());

        verify(annotationSidecarService).writeSidecar(eq(book), eq(userEntity), anyList());
    }

    @Test
    void deleteAnnotation_callsWriteSidecarWithRemainingAnnotations() {
        AnnotationEntity existing = mock(AnnotationEntity.class);
        when(existing.getBook()).thenReturn(book);
        when(existing.getUser()).thenReturn(userEntity);
        when(annotationRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(existing));

        AnnotationEntity remaining = mock(AnnotationEntity.class);
        when(annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(10L, 1L))
                .thenReturn(List.of(remaining));

        service.deleteAnnotation(99L);

        verify(annotationSidecarService).writeSidecar(book, userEntity, List.of(remaining));
    }
}
