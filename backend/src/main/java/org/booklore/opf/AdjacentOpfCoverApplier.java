package org.booklore.opf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.metadata.BookCoverService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdjacentOpfCoverApplier implements BookScanMetadataAugmenter {

    private final AdjacentOpfLocator opfLocator;
    private final AdjacentOpfCoverLocator coverLocator;
    private final ObjectProvider<BookCoverService> bookCoverService;

    @Override
    public void augment(LibraryFile libraryFile, BookEntity bookEntity) {
        if (bookEntity == null || bookEntity.getMetadata() == null || Boolean.TRUE.equals(bookEntity.getMetadata().getCoverLocked())) {
            return;
        }

        opfLocator.find(libraryFile)
                .flatMap(opf -> coverLocator.find(opf, libraryFile))
                .ifPresent(cover -> {
                    boolean applied = bookCoverService.getObject().applyLocalCoverCacheOnly(bookEntity, cover);
                    if (!applied) {
                        log.warn("Adjacent OPF cover was found but not applied for book ID {}: {}", bookEntity.getId(), cover);
                    }
                });
    }
}
