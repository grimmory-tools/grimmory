package org.booklore.service.book;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.repository.PdfAnnotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import org.booklore.model.entity.PdfAnnotationEntity;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfAnnotationService {

    private final PdfAnnotationRepository pdfAnnotationRepository;
    private final AuthenticationService authenticationService;
    private final PdfAnnotationPersistenceHandler pdfAnnotationPersistenceHandler;

    @Transactional(readOnly = true)
    public Optional<String> getAnnotations(Long bookId) {
        Long userId = getCurrentUserId();
        return pdfAnnotationRepository.findByBookIdAndUserId(bookId, userId)
                .map(PdfAnnotationEntity::getData);
    }

    /**
     * Orchestrates the save/update of PDF annotations with a retry mechanism.
     * The actual database operation is handled by {@link PdfAnnotationPersistenceHandler}
     * in its own transaction (REQUIRES_NEW) to ensure that concurrency failures
     * don't poison the main request transaction.
     */
    public void saveAnnotations(Long bookId, String data) {
        Long userId = getCurrentUserId();
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                pdfAnnotationPersistenceHandler.saveOrUpdate(bookId, userId, data);
                return;
            } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
                retryCount++;
                if (retryCount >= maxRetries) {
                    log.error("Failed to save PDF annotations for book {}/user {} after {} retries: {}", 
                            bookId, userId, maxRetries, e.getMessage());
                    throw e; // Final failure, pass to exception handler
                }
                log.info("Concurrent update for PDF annotations (book {}, user {}), retrying (attempt {}/{}). Reason: {}", 
                        bookId, userId, retryCount, maxRetries, e.getMessage());
                
                try {
                    Thread.sleep(50); // Small backoff before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
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
}
