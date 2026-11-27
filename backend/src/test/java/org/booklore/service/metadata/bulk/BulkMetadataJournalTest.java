package org.booklore.service.metadata.bulk;

import org.booklore.model.entity.BulkMetadataJournalEntity;
import org.booklore.repository.BulkMetadataJournalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BulkMetadataJournalTest {

    @Test
    void requireAttentionRetainsEvidenceAndReleasesBookReservation() {
        BulkMetadataJournalRepository repository = mock(BulkMetadataJournalRepository.class);
        BulkMetadataJournalEntity entry = BulkMetadataJournalEntity.builder()
                .operationId("753fb9b2-60c4-4451-8be9-3b4b2ba14a58")
                .jobId("job")
                .bookId(50057L)
                .activeBookId(50057L)
                .stage(BulkMetadataJournalEntity.Stage.EPUB_PREPARING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(repository.findForUpdate(entry.getOperationId())).thenReturn(Optional.of(entry));
        when(repository.saveAndFlush(entry)).thenReturn(entry);

        new BulkMetadataJournal(repository).requireAttention(entry.getOperationId(), "EPUB_UPDATE_FAILED");

        assertThat(entry.getStage()).isEqualTo(BulkMetadataJournalEntity.Stage.NEEDS_ATTENTION);
        assertThat(entry.getPreviousStage()).isEqualTo(BulkMetadataJournalEntity.Stage.EPUB_PREPARING);
        assertThat(entry.getActiveBookId()).isNull();
        assertThat(entry.getErrorCode()).isEqualTo("EPUB_UPDATE_FAILED");
    }
}
