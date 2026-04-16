package org.booklore.service.book;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.PdfAnnotationEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.PdfAnnotationRepository;
import org.booklore.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Handles the actual database persistence for PDF annotations in isolated transactions.
 * This ensures that flush failures (optimistic locking or constraints) don't poison
 * the caller's transaction and allow for clean retries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfAnnotationPersistenceHandler {

    private final PdfAnnotationRepository pdfAnnotationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveOrUpdate(Long bookId, Long userId, String data) {
        Optional<PdfAnnotationEntity> existing = pdfAnnotationRepository.findByBookIdAndUserId(bookId, userId);

        if (existing.isPresent()) {
            PdfAnnotationEntity entity = existing.get();
            // Version check is performed by Hibernate on flush
            entity.setData(data);
            pdfAnnotationRepository.saveAndFlush(entity);
            log.info("Updated PDF annotations for book {} by user {}", bookId, userId);
        } else {
            PdfAnnotationEntity entity = PdfAnnotationEntity.builder()
                    .book(findBook(bookId))
                    .user(findUser(userId))
                    .data(data)
                    .build();
            // Unique constraint check happens on flush
            pdfAnnotationRepository.saveAndFlush(entity);
            log.info("Created PDF annotations for book {} by user {}", bookId, userId);
        }
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
