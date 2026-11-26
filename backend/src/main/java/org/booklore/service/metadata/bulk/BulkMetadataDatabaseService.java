package org.booklore.service.metadata.bulk;

import lombok.AllArgsConstructor;
import org.booklore.exception.ApiError;
import org.booklore.model.MetadataUpdateContext;
import org.booklore.model.MetadataUpdateWrapper;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.enums.MetadataReplaceMode;
import org.booklore.repository.BookRepository;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Objects;

@Service
@AllArgsConstructor
public class BulkMetadataDatabaseService {
    private final BulkMetadataJournal journal;
    private final BookRepository bookRepository;
    private final BookMetadataUpdater updater;

    @Transactional
    public void apply(String operationId, long bookId, BookMetadata fetched, MetadataReplaceMode replaceMode,
                      boolean mergeCategories) {
        Objects.requireNonNull(fetched);
        Objects.requireNonNull(replaceMode);
        journal.lockPendingDatabase(operationId, bookId);
        BookEntity book = bookRepository.findAllWithMetadataByIds(Collections.singleton(bookId))
                .stream().findFirst()
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        updater.setBookMetadata(MetadataUpdateContext.builder()
                .bookEntity(book)
                .metadataUpdateWrapper(MetadataUpdateWrapper.builder().metadata(fetched).build())
                .updateThumbnail(false)
                .mergeCategories(mergeCategories)
                .mergeMoods(true)
                .mergeTags(true)
                .replaceMode(replaceMode)
                .databaseOnly(true)
                .build());
        bookRepository.saveAndFlush(book);
        journal.recordDatabaseCommit(operationId);
    }
}
