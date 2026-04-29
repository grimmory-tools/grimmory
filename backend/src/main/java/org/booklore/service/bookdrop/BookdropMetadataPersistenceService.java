package org.booklore.service.bookdrop;

import lombok.RequiredArgsConstructor;
import org.booklore.model.entity.BookdropFileEntity;
import org.booklore.repository.BookdropFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BookdropMetadataPersistenceService {

    private final BookdropFileRepository bookdropFileRepository;

    @Transactional
    public BookdropFileEntity saveRefetchedMetadata(BookdropFileEntity entity, String fetchedJson, BookdropFileEntity.Status status) {
        entity.setFetchedMetadata(fetchedJson);
        if (status != null) {
            entity.setStatus(status);
        }
        entity.setUpdatedAt(Instant.now());
        return bookdropFileRepository.save(entity);
    }
}
