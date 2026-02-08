package org.booklore.repository;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.projection.BookCoverUpdateProjection;
import jakarta.transaction.Transactional;
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

    @EntityGraph(attributePaths = { "metadata", "shelves", "libraryPath", "library", "bookFiles" })
    @Query("SELECT b FROM BookEntity b LEFT JOIN FETCH b.bookFiles bf WHERE b.id = :id AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByIdWithBookFiles(@Param("id") Long id);

    @Query("SELECT b FROM BookEntity b JOIN b.bookFiles bf WHERE bf.currentHash = :currentHash AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByCurrentHash(@Param("currentHash") String currentHash);

    Optional<BookEntity> findByBookCoverHash(String bookCoverHash);

    @Query("SELECT b.id FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    Set<Long> findBookIdsByLibraryId(@Param("libraryId") long libraryId);

    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath LIKE CONCAT(:fileSubPathPrefix, '%') AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryPathIdAndFileSubPathStartingWith(@Param("libraryPathId") Long libraryPathId, @Param("fileSubPathPrefix") String fileSubPathPrefix);

    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByLibraryPathIdAndFileSubPath(@Param("libraryPathId") Long libraryPathId, @Param("fileSubPath") String fileSubPath);

    @Query("SELECT b FROM BookEntity b JOIN b.bookFiles bf WHERE b.libraryPath.id = :libraryPathId AND bf.fileSubPath = :fileSubPath AND bf.fileName = :fileName AND bf.isBookFormat = true AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByLibraryPath_IdAndFileSubPathAndFileName(@Param("libraryPathId") Long libraryPathId,
                                                                       @Param("fileSubPath") String fileSubPath,
                                                                       @Param("fileName") String fileName);

    @Query("SELECT b.id FROM BookEntity b WHERE b.libraryPath.id IN :libraryPathIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<Long> findAllBookIdsByLibraryPathIdIn(@Param("libraryPathIds") Collection<Long> libraryPathIds);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadata();

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("bookIds") Set<Long> bookIds);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findWithMetadataByIdsWithPagination(@Param("bookIds") Set<Long> bookIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryId(@Param("libraryId") Long libraryId);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByShelfId(@Param("shelfId") Long shelfId);

    @EntityGraph(attributePaths = { "metadata", "shelves", "libraryPath", "bookFiles" })
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.bookFiles bf WHERE bf.isBookFormat = true AND bf.fileSizeKb IS NULL AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByFileSizeKbIsNull();

    @Query("""
                SELECT DISTINCT b FROM BookEntity b
                LEFT JOIN FETCH b.metadata m
                LEFT JOIN FETCH m.authors
                LEFT JOIN FETCH m.categories
                LEFT JOIN FETCH b.shelves
                WHERE (b.deleted IS NULL OR b.deleted = false)
            """)
    List<BookEntity> findAllFullBooks();

    @Query(value = """
                SELECT DISTINCT b.* FROM book b
                LEFT JOIN book_metadata m ON b.id = m.book_id
                WHERE (b.deleted IS NULL OR b.deleted = false)
                ORDER BY b.id
                LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<BookEntity> findBooksForMigrationBatch(@Param("offset") int offset, @Param("limit") int limit);

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

    @Query(value = """
        SELECT b.*
        FROM book b
        JOIN book_file bf ON bf.book_id = b.id
        WHERE b.library_id = :libraryId
          AND b.library_path_id = :libraryPathId
          AND bf.file_sub_path = :fileSubPath
          AND bf.file_name = :fileName
          AND bf.is_book = true
        LIMIT 1
    """, nativeQuery = true)
    Optional<BookEntity> findByLibraryIdAndLibraryPathIdAndFileSubPathAndFileName(
            @Param("libraryId") Long libraryId,
            @Param("libraryPathId") Long libraryPathId,
            @Param("fileSubPath") String fileSubPath,
            @Param("fileName") String fileName);

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
    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
    @Query("""
            SELECT DISTINCT b FROM BookEntity b
            LEFT JOIN b.metadata m
            LEFT JOIN b.bookFiles bf
            WHERE b.library.id = :libraryId
            AND (
                (m.seriesName = :seriesName)
                OR (
                    m.seriesName IS NULL
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
    List<BookEntity> findBooksBySeriesNameGroupedByLibraryId(
            @Param("seriesName") String seriesName,
            @Param("libraryId") Long libraryId,
            @Param("unknownSeriesName") String unknownSeriesName);

    /**
     * Find books by series name for a library when groupUnknown=false.
     * Matches by series name, or by title/filename for books without series.
     */
    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath", "bookFiles"})
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
}
