package org.booklore.grimmlink.repository;

import org.booklore.grimmlink.model.GrimmlinkMetadataItemEntity;
import org.booklore.grimmlink.model.GrimmlinkMetadataItemType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GrimmlinkMetadataItemRepository extends JpaRepository<GrimmlinkMetadataItemEntity, Long> {

    Optional<GrimmlinkMetadataItemEntity> findByUserIdAndBookIdAndItemTypeAndDedupeKey(
            Long userId,
            Long bookId,
            GrimmlinkMetadataItemType itemType,
            String dedupeKey);

    @Query("""
            SELECT item FROM GrimmlinkMetadataItemEntity item
            JOIN FETCH item.book book
            LEFT JOIN FETCH item.bookFile bookFile
            WHERE item.user.id = :userId
              AND book.id = :bookId
              AND (:bookFileId IS NULL OR bookFile.id = :bookFileId)
              AND (:itemType IS NULL OR item.itemType = :itemType)
              AND (:since IS NULL OR item.updatedAt > :since)
            ORDER BY item.updatedAt ASC, item.id ASC
            """)
    List<GrimmlinkMetadataItemEntity> findPullItems(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId,
            @Param("bookFileId") Long bookFileId,
            @Param("itemType") GrimmlinkMetadataItemType itemType,
            @Param("since") Instant since,
            Pageable pageable);
}
