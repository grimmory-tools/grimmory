package org.booklore.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.projection.BookCoverUpdateProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long>, JpaSpecificationExecutor<BookEntity> {
    Optional<BookEntity> findBookByIdAndLibraryId(long id, long libraryId);

    @EntityGraph(attributePaths = { "metadata", "metadata.authors", "bookFiles", "libraryPath" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdForAudiobook(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "metadata.comicMetadata", "shelves", "libraryPath", "library", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithBookFiles(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "bookFiles", "libraryPath", "library" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdForStreaming(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags", "metadata.comicMetadata", "library" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithMetadata(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags", "metadata.comicMetadata", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdFull(@Param("id") Long id);

    @EntityGraph(attributePaths = { "metadata", "metadata.authors", "metadata.categories", "metadata.tags", "metadata.comicMetadata", "libraryPath", "library", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdForKoboDownload(@Param("id") Long id);

    // Minimal graph for summary mapping: metadata (OneToOne), library (ManyToOne), bookFiles (OneToMany).
    // metadata.authors is intentionally excluded — @BatchSize on BookMetadataEntity.authors
    // triggers a batched query when first accessed, so no N+1 occurs.
    @EntityGraph(attributePaths = { "metadata", "library", "bookFiles" })
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllForSummaryByIds(@Param("bookIds") Collection<Long> bookIds);

    @EntityGraph(attributePaths = {"bookFiles", "metadata", "library", "libraryPath"})
    @Query("SELECT b FROM BookEntity b JOIN b.bookFiles bf WHERE bf.currentHash = :currentHash AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByCurrentHash(@Param("currentHash") String currentHash);

    @Query("SELECT b FROM BookEntity b JOIN FETCH b.bookFiles bf WHERE bf.currentHash = :currentHash AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false OR b.deletedAt > :cutoff)")
    Optional<BookEntity> findByCurrentHashIncludingRecentlyDeleted(@Param("currentHash") String currentHash, @Param("cutoff") Instant cutoff);

    Optional<BookEntity> findByBookCoverHash(String bookCoverHash);

    @Query("SELECT b.id FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    Set<Long> findBookIdsByLibraryId(@Param("libraryId") long libraryId);

    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND (bf.fileSubPath = :fileSubPathPrefix OR bf.fileSubPath LIKE CONCAT(:fileSubPathPrefix, '/%')) AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryPathIdAndFileSubPathStartingWith(@Param("libraryPathId") Long libraryPathId, @Param("fileSubPathPrefix") String fileSubPathPrefix);

    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryPathIdAndFileSubPath(@Param("libraryPathId") Long libraryPathId, @Param("fileSubPath") String fileSubPath);

    @Query("SELECT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.fileName = :fileName AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByLibraryPath_IdAndFileSubPathAndFileName(@Param("libraryPathId") Long libraryPathId,
                                                                       @Param("fileSubPath") String fileSubPath,
                                                                       @Param("fileName") String fileName);

    @Query("SELECT b.id FROM BookEntity b WHERE b.libraryPath.id IN :libraryPathIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<Long> findAllBookIdsByLibraryPathIdIn(@Param("libraryPathIds") Collection<Long> libraryPathIds);

    // Only ToOne paths in EntityGraph; collections (authors, categories, moods, tags, shelves, bookFiles) loaded via @BatchSize.
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "library"})
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadata();

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("bookIds") Set<Long> bookIds);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryId(@Param("libraryId") Long libraryId);

    @EntityGraph(attributePaths = {"metadata", "bookFiles", "library"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryIdWithFiles(@Param("libraryId") Long libraryId);

    @EntityGraph(attributePaths = {"bookFiles", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId")
    List<BookEntity> findAllByLibraryIdForRescan(@Param("libraryId") Long libraryId);

    @EntityGraph(attributePaths = {"bookFiles", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND b.deleted = true")
    List<BookEntity> findDeletedByLibraryIdWithFiles(@Param("libraryId") Long libraryId);

    // Only ToOne paths in EntityGraph; collections (authors, bookFiles) loaded via @BatchSize.
    @EntityGraph(attributePaths = {"metadata", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllForDuplicateDetection(@Param("libraryId") Long libraryId);

    // Only ToOne paths in EntityGraph; collections (authors, categories, moods, tags, shelves, bookFiles) loaded via @BatchSize.
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "library"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    @EntityGraph(attributePaths = {
        "metadata", "metadata.comicMetadata",
        "metadata.comicMetadata.characters", "metadata.comicMetadata.teams", "metadata.comicMetadata.locations", "metadata.comicMetadata.creatorMappings",
        "metadata.authors", "metadata.categories", "metadata.moods", "metadata.tags",
        "shelves", "libraryPath", "library", "bookFiles"
    })
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByShelfId(@Param("shelfId") Long shelfId);

    @EntityGraph(attributePaths = { "metadata", "metadata.comicMetadata", "shelves", "libraryPath", "library", "bookFiles" })
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE bf.isBookFormat = true AND bf.fileSizeKb IS NULL AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByFileSizeKbIsNull();

    // Only ToOne paths in EntityGraph; collections (authors, categories, shelves) loaded via @BatchSize.
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata"})
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllFullBooks();

    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) AND b.id > :afterId ORDER BY b.id")
    List<BookEntity> findBooksForMigrationBatch(@Param("afterId") long afterId, Pageable pageable);

    @Query("""
                SELECT DISTINCT b FROM BookEntity b
                LEFT JOIN FETCH b.metadata m
                LEFT JOIN FETCH m.authors
                WHERE b.id IN :bookIds
            """)
    List<BookEntity> findBooksWithMetadataAndAuthors(@Param("bookIds") List<Long> bookIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM BookEntity b WHERE b.deleted IS TRUE")
    int deleteAllSoftDeleted();

    @Modifying
    @Transactional
    @Query("DELETE FROM BookEntity b WHERE b.deleted IS TRUE AND b.deletedAt < :cutoffDate")
    int deleteSoftDeletedBefore(@Param("cutoffDate") Instant cutoffDate);

    @Query("SELECT COUNT(b) FROM BookEntity b WHERE b.deleted = TRUE")
    long countAllSoftDeleted();

    @Query("""
        SELECT DISTINCT b FROM BookEntity b
        JOIN FETCH b.bookFiles bf
        WHERE b.libraryPath.id = :libraryPathId
        AND (bf.fileSubPath = :folderPath
             OR bf.fileSubPath LIKE CONCAT(:folderPath, '/%')
             OR (bf.folderBased = true AND CONCAT(bf.fileSubPath, '/', bf.fileName) = :folderPath))
        AND bf.isBookFormat = true
        AND (b.deleted IS NULL OR b.deleted = false)
        """)
    List<BookEntity> findBooksWithFilesUnderPath(@Param("libraryPathId") Long libraryPathId,
                                                  @Param("folderPath") String folderPath);

    @Query("""
        SELECT b FROM BookEntity b
        JOIN b.bookFiles bf
        WHERE b.library.id = :libraryId
          AND b.libraryPath.id = :libraryPathId
          AND bf.fileSubPath = :fileSubPath
          AND bf.fileName = :fileName
          AND bf.isBookFormat = true
        ORDER BY b.id ASC
        """)
    List<BookEntity> findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
            @Param("libraryId") Long libraryId,
            @Param("libraryPathId") Long libraryPathId,
            @Param("fileSubPath") String fileSubPath,
            @Param("fileName") String fileName,
            Pageable pageable);

    default Optional<BookEntity> findFirstByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
            Long libraryId, Long libraryPathId, String fileSubPath, String fileName) {
        return findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
                libraryId, libraryPathId, fileSubPath, fileName, PageRequest.of(0, 1))
                .stream().findFirst();
    }

    @Query("SELECT COUNT(b.id) FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    long countByIdIn(@Param("bookIds") List<Long> bookIds);

    @Query("""
            SELECT COUNT(DISTINCT b) FROM BookEntity b
            JOIN b.bookFiles bf
            WHERE bf.isBookFormat = true
              AND bf.bookType = :type
              AND (b.deleted IS NULL OR b.deleted = false)
            """)
    long countByBookType(@Param("type") BookFileType type);

    @Query("""
            SELECT COUNT(DISTINCT b) FROM BookEntity b
            JOIN b.bookFiles bf
            WHERE b.library.id = :libraryId
              AND bf.isBookFormat = true
              AND bf.bookType = :type
              AND (b.deleted IS NULL OR b.deleted = false)
            """)
    long countByLibraryIdAndBookType(@Param("libraryId") Long libraryId, @Param("type") BookFileType type);

    @Query("SELECT COUNT(b) FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    long countByLibraryId(@Param("libraryId") Long libraryId);

    @Query("""
            SELECT b FROM BookEntity b
            LEFT JOIN b.bookFiles bf
            WHERE b.library.id = :libraryId
            AND (b.deleted IS NULL OR b.deleted = false)
            GROUP BY b
            HAVING COUNT(bf) = 0
            """)
    List<BookEntity> findFilelessBooksByLibraryId(@Param("libraryId") Long libraryId);

    @Query("SELECT b.id as id, m.coverUpdatedOn as coverUpdatedOn FROM BookEntity b LEFT JOIN b.metadata m WHERE b.id IN :bookIds")
    List<BookCoverUpdateProjection> findCoverUpdateInfoByIds(@Param("bookIds") Collection<Long> bookIds);

    @Modifying
    @Query("""
            UPDATE BookEntity b SET
                b.library.id = :libraryId,
                b.libraryPath = :libraryPath
            WHERE b.id = :bookId
            """)
    void updateLibrary(
            @Param("bookId") Long bookId,
            @Param("libraryId") Long libraryId,
            @Param("libraryPath") LibraryPathEntity libraryPath);

    /**
     * Get distinct series names for a library when groupUnknown=true.
     * Books without series name are grouped as "Unknown Series".
     */
    @Query("""
            SELECT DISTINCT 
                CASE 
                    WHEN m.seriesName IS NOT NULL THEN m.seriesName
                    ELSE :unknownSeriesName
                END as seriesName
            FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE b.library.id = :libraryId 
            AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY seriesName
            """)
    List<String> findDistinctSeriesNamesGroupedByLibraryId(
            @Param("libraryId") Long libraryId,
            @Param("unknownSeriesName") String unknownSeriesName);

    /**
     * Get distinct series names across all libraries when groupUnknown=true.
     * Books without series name are grouped as "Unknown Series".
     */
    @Query("""
            SELECT DISTINCT 
                CASE 
                    WHEN m.seriesName IS NOT NULL THEN m.seriesName
                    ELSE :unknownSeriesName
                END as seriesName
            FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE (b.deleted IS NULL OR b.deleted = false)
            ORDER BY seriesName
            """)
    List<String> findDistinctSeriesNamesGrouped(@Param("unknownSeriesName") String unknownSeriesName);

    /**
     * Get distinct series names for a library when groupUnknown=false.
     * Each book without series gets its own entry (title or filename).
     */
    @Query("""
            SELECT DISTINCT 
                CASE 
                    WHEN m.seriesName IS NOT NULL THEN m.seriesName
                    WHEN m.title IS NOT NULL THEN m.title
                    ELSE (
                        SELECT bf2.fileName FROM BookFileEntity bf2
                        WHERE bf2.book = b
                          AND bf2.isBookFormat = true
                          AND bf2.id = (
                              SELECT MIN(bf3.id) FROM BookFileEntity bf3
                              WHERE bf3.book = b AND bf3.isBookFormat = true
                          )
                    )
                END as seriesName
            FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE b.library.id = :libraryId 
            AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY seriesName
            """)
    List<String> findDistinctSeriesNamesUngroupedByLibraryId(@Param("libraryId") Long libraryId);

    /**
     * Get distinct series names across all libraries when groupUnknown=false.
     * Each book without series gets its own entry (title or filename).
     */
    @Query("""
            SELECT DISTINCT 
                CASE 
                    WHEN m.seriesName IS NOT NULL THEN m.seriesName
                    WHEN m.title IS NOT NULL THEN m.title
                    ELSE (
                        SELECT bf2.fileName FROM BookFileEntity bf2
                        WHERE bf2.book = b
                          AND bf2.isBookFormat = true
                          AND bf2.id = (
                              SELECT MIN(bf3.id) FROM BookFileEntity bf3
                              WHERE bf3.book = b AND bf3.isBookFormat = true
                          )
                    )
                END as seriesName
            FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE (b.deleted IS NULL OR b.deleted = false)
            ORDER BY seriesName
            """)
    List<String> findDistinctSeriesNamesUngrouped();

    /**
     * Find books by series name for a library when groupUnknown=true.
     * Uses the first bookFile.fileName as fallback when metadata.seriesName is null.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "shelves", "libraryPath", "bookFiles"})
    @Query("""
            SELECT DISTINCT b FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE b.library.id = :libraryId
            AND (
                (m.seriesName = :seriesName)
                OR (m.seriesName IS NULL AND :seriesName = :unknownSeriesName)
            )
            AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY COALESCE(m.seriesNumber, 0)
            """)
    List<BookEntity> findBooksBySeriesNameGroupedByLibraryId(
            @Param("seriesName") String seriesName,
            @Param("libraryId") Long libraryId,
            @Param("unknownSeriesName") String unknownSeriesName);

    /**
     * Find books by series name for a library when groupUnknown=false.
     * Matches by series name, or by title/filename for books without series.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "shelves", "libraryPath", "bookFiles"})
    @Query("""
            SELECT b FROM BookEntity b
            LEFT JOIN b.metadata m
            LEFT JOIN b.bookFiles bf
            WHERE b.library.id = :libraryId
            AND (
                (m.seriesName = :seriesName)
                OR (m.seriesName IS NULL AND m.title = :seriesName)
                OR (
                    m.seriesName IS NULL AND m.title IS NULL
                    AND bf.isBookFormat = true
                    AND bf.id = (
                        SELECT MIN(bf2.id) FROM BookFileEntity bf2
                        WHERE bf2.book = b AND bf2.isBookFormat = true
                    )
                    AND bf.fileName = :seriesName
                )
            )
            AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY COALESCE(m.seriesNumber, 0)
            """)
    List<BookEntity> findBooksBySeriesNameUngroupedByLibraryId(
            @Param("seriesName") String seriesName,
            @Param("libraryId") Long libraryId);

    /**
     * Find books by series name across all libraries when groupUnknown=true.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "shelves", "libraryPath", "bookFiles"})
    @Query("""
            SELECT DISTINCT b FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE (
                (m.seriesName = :seriesName)
                OR (
                    m.seriesName IS NULL
                    AND :seriesName = :unknownSeriesName
                )
            )
            AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY COALESCE(m.seriesNumber, 0)
            """)
    List<BookEntity> findBooksBySeriesNameGrouped(
            @Param("seriesName") String seriesName,
            @Param("unknownSeriesName") String unknownSeriesName);

    /**
     * Find books by series name across all libraries when groupUnknown=false.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "shelves", "libraryPath", "bookFiles"})
    @Query("""
            SELECT b FROM BookEntity b
            LEFT JOIN b.metadata m
            LEFT JOIN b.bookFiles bf
            WHERE (
                (m.seriesName = :seriesName)
                OR (m.seriesName IS NULL AND m.title = :seriesName)
                OR (
                    m.seriesName IS NULL AND m.title IS NULL
                    AND bf.isBookFormat = true
                    AND bf.id = (
                        SELECT MIN(bf2.id) FROM BookFileEntity bf2
                        WHERE bf2.book = b AND bf2.isBookFormat = true
                    )
                    AND bf.fileName = :seriesName
                )
            )
            AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY COALESCE(m.seriesNumber, 0)
            """)
    List<BookEntity> findBooksBySeriesNameUngrouped(
            @Param("seriesName") String seriesName);

    /**
     * Paginated query for all non-deleted books with Komga-relevant metadata.
     * Only ToOne paths in EntityGraph to avoid Cartesian product; collections loaded via @BatchSize.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "libraryPath", "library"})
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    Page<BookEntity> findAllWithMetadataPaged(Pageable pageable);

    /**
     * Paginated query for non-deleted books by library with Komga-relevant metadata.
     * Only ToOne paths in EntityGraph to avoid Cartesian product; collections loaded via @BatchSize.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "libraryPath", "library"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    Page<BookEntity> findAllWithMetadataByLibraryIdPaged(@Param("libraryId") Long libraryId, Pageable pageable);

    @Query("SELECT COUNT(b) FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    long countNonDeleted();

    @Query("SELECT COUNT(b) FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    long countByLibraryIdNonDeleted(@Param("libraryId") Long libraryId);

    /**
     * Lightweight query for recommendation: loads only metadata with authors and categories (no bookFiles, shelves, etc.)
     */
    @Query("""
            SELECT DISTINCT b FROM BookEntity b
            LEFT JOIN FETCH b.metadata m
            LEFT JOIN FETCH m.authors
            LEFT JOIN FETCH m.categories
            WHERE (b.deleted IS NULL OR b.deleted = false)
            AND b.id <> :excludeBookId
            """)
    List<BookEntity> findAllForRecommendation(@Param("excludeBookId") Long excludeBookId);

    /**
     * Paginated query for all non-deleted books (main UI listing).
     * Only ToOne paths in EntityGraph to avoid Cartesian product with LIMIT;
     * collections (authors, categories, tags, moods, shelves, bookFiles) loaded via @BatchSize.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "libraryPath", "library"})
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    Page<BookEntity> findAllWithMetadataPage(Pageable pageable);

    /**
     * Paginated query for non-deleted books filtered by library IDs.
     * Only ToOne paths in EntityGraph to avoid Cartesian product with LIMIT;
     * collections (authors, categories, tags, moods, shelves, bookFiles) loaded via @BatchSize.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata", "libraryPath", "library"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    Page<BookEntity> findAllWithMetadataByLibraryIdsPage(@Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    /**
     * Batched query for embedding/recommendation computation.
     * Only ToOne paths in EntityGraph to avoid Cartesian product with LIMIT/OFFSET;
     * collections (authors, categories, shelves) loaded via @BatchSize when accessed.
     */
    @EntityGraph(attributePaths = {"metadata", "metadata.comicMetadata"})
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) ORDER BY b.id")
    List<BookEntity> findAllFullBooksBatch(Pageable pageable);

    /**
     * Lightweight projection for on-demand recommendation: returns only bookId, embedding vector,
     * and series name. Avoids loading full entities when pre-computed embeddings are available.
     */
    @Query("""
            SELECT b.id as bookId, m.embeddingVector as embeddingVector, m.seriesName as seriesName
            FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE (b.deleted IS NULL OR b.deleted = false)
            AND b.id <> :excludeBookId
            AND m.embeddingVector IS NOT NULL
            """)
    List<org.booklore.repository.projection.BookEmbeddingProjection> findAllEmbeddingsForRecommendation(@Param("excludeBookId") Long excludeBookId);

}
