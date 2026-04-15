package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.PdfAnnotationEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.PdfAnnotationRepository;
import org.booklore.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfAnnotationService {

    private final PdfAnnotationRepository pdfAnnotationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;

    @Transactional(readOnly = true)
    public Optional<String> getAnnotations(Long bookId) {
        Long userId = getCurrentUserId();
        return pdfAnnotationRepository.findByBookIdAndUserId(bookId, userId)
                .map(PdfAnnotationEntity::getData);
    }

    @Transactional
    public void saveAnnotations(Long bookId, String data) {
        Long userId = getCurrentUserId();
        try {
            Optional<PdfAnnotationEntity> existing = pdfAnnotationRepository.findByBookIdAndUserId(bookId, userId);

            if (existing.isPresent()) {
                PdfAnnotationEntity entity = existing.get();
                entity.setData(data);
                pdfAnnotationRepository.saveAndFlush(entity);
                log.info("Updated PDF annotations for book {} by user {}", bookId, userId);
            } else {
                PdfAnnotationEntity entity = PdfAnnotationEntity.builder()
                        .book(findBook(bookId))
                        .user(findUser(userId))
                        .data(data)
                        .build();
                pdfAnnotationRepository.saveAndFlush(entity);
                log.info("Created PDF annotations for book {} by user {}", bookId, userId);
            }
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent update detected for PDF annotations (book {}, user {}), ignoring redundant save.", bookId, userId);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent creation detected for PDF annotations (book {}, user {}), retrying as update.", bookId, userId);
            pdfAnnotationRepository.findByBookIdAndUserId(bookId, userId).ifPresent(entity -> {
                entity.setData(data);
                pdfAnnotationRepository.save(entity);
            });
        }
    }

    @Transactional
    public void deleteAnnotations(Long bookId) {
        Long userId = getCurrentUserId();
        pdfAnnotationRepository.deleteByBookIdAndUserId(bookId, userId);
        log.info("Deleted PDF annotations for book {} by user {}", bookId, userId);
    }

    private Long getCurrentUserId() {
        return authenticationService.getAuthenticatedUser().getId();
    }

    private BookEntity findBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new EntityNotFoundException("Book not found: " + bookId));
    }

    private BookLoreUserEntity findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }
}
