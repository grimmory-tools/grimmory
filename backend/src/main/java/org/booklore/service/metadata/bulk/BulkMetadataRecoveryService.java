package org.booklore.service.metadata.bulk;

import lombok.AllArgsConstructor;
import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.booklore.repository.BulkMetadataJournalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class BulkMetadataRecoveryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final BulkMetadataJournalRepository repository;

    public record RecoveryItem(String operationId, long bookId, BulkMetadataJournalEntity.Stage stage,
                               String errorCode, BulkMetadataRecoveryInspector.Inspection files) {}

    @Transactional(readOnly = true)
    public Page<RecoveryItem> inspect(String jobId, int page, int size) {
        if (jobId == null || jobId.isBlank() || jobId.length() > 100 || page < 0
                || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid bulk metadata recovery page");
        }
        return repository.findByJobIdOrderByOperationId(jobId, PageRequest.of(page, size))
                .map(entry -> new RecoveryItem(entry.getOperationId(), entry.getBookId(), entry.getStage(),
                        entry.getErrorCode(), BulkMetadataRecoveryInspector.inspect(entry)));
    }
}
