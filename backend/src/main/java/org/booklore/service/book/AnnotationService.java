package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.AnnotationMapper;
import org.booklore.model.dto.Annotation;
import org.booklore.model.dto.CreateAnnotationRequest;
import org.booklore.model.dto.UpdateAnnotationRequest;
import org.booklore.model.entity.AnnotationEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.AnnotationRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final AnnotationMapper mapper;

    @Transactional(readOnly = true)
    public List<Annotation> getAnnotationsForBook(Long bookId) {
        Long userId = getCurrentUserId();
        return annotationRepository.findByBookIdAndUserIdOrderByCreatedAtDesc(bookId, userId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Annotation getAnnotationById(Long annotationId) {
        return mapper.toDto(findAnnotationByIdAndUser(annotationId));
    }

    @Transactional
    public Annotation createAnnotation(CreateAnnotationRequest request) {
        Long userId = getCurrentUserId();
        validateNoDuplicateAnnotation(request.getCfi(), request.getBookId(), userId);

        String color = request.getColor() != null ? request.getColor() : "#FFFF00";
        String style = request.getStyle() != null ? request.getStyle() : "highlight";

        AnnotationEntity annotation = AnnotationEntity.builder()
                .cfi(request.getCfi())
                .text(request.getText())
                .color(color)
                .style(style)
                .note(request.getNote())
                .chapterTitle(request.getChapterTitle())
                .book(findBook(request.getBookId()))
                .user(findUser(userId))
                .build();

        log.info("Creating annotation for book {} by user {}", request.getBookId(), userId);
        return mapper.toDto(annotationRepository.save(annotation));
    }

    @Transactional
    public Annotation updateAnnotation(Long annotationId, UpdateAnnotationRequest request) {
        AnnotationEntity annotation = findAnnotationByIdAndUser(annotationId);

        applyUpdates(annotation, request);

        log.info("Updating annotation {}", annotationId);
        return mapper.toDto(annotationRepository.save(annotation));
    }

    @Transactional
    public void deleteAnnotation(Long annotationId) {
        AnnotationEntity annotation = findAnnotationByIdAndUser(annotationId);
        log.info("Deleting annotation {}", annotationId);
        annotationRepository.delete(annotation);
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }

    private AnnotationEntity findAnnotationByIdAndUser(Long annotationId) {
        Long userId = getCurrentUserId();
        return annotationRepository.findByIdAndUserId(annotationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Annotation not found: " + annotationId));
    }

    private BookEntity findBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + bookId));
    }

    private BookLoreUserEntity findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private void validateNoDuplicateAnnotation(String cfi, Long bookId, Long userId) {
        boolean exists = annotationRepository.existsByCfiAndBookIdAndUserId(cfi, bookId, userId);
        if (exists) {
            throw new APIException("Annotation already exists at this location", HttpStatus.CONFLICT);
        }
    }

    private void applyUpdates(AnnotationEntity annotation, UpdateAnnotationRequest request) {
        Optional.ofNullable(request.getColor()).ifPresent(annotation::setColor);
        Optional.ofNullable(request.getStyle()).ifPresent(annotation::setStyle);
        Optional.ofNullable(request.getNote()).ifPresent(annotation::setNote);
    }
}
