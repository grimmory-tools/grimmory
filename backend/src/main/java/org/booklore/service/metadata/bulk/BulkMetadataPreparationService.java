package org.booklore.service.metadata.bulk;

import lombok.AllArgsConstructor;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.service.metadata.writer.EpubMetadataWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;

@Service
@AllArgsConstructor
public class BulkMetadataPreparationService {
    private final BulkMetadataJournal journal;
    private final EpubMetadataWriter epubWriter;

    public String prepare(String operationId, BulkMetadataJournal.EpubPreparationIntent intent,
                          BookMetadataEntity metadata, MetadataClearFlags clear) throws IOException {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("EPUB preparation must start outside a transaction");
        }
        journal.recordEpubPreparationIntent(operationId, intent);
        String candidateHash = epubWriter.prepareMetadataCandidate(intent.source(), intent.candidate(), intent.backup(),
                intent.originalSha256(), metadata, clear);
        journal.recordEpubPrepared(operationId, candidateHash);
        return candidateHash;
    }
}
