package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long>, JpaSpecificationExecutor<BookEntity> {
    Optional<BookEntity> findBookByIdAndLibraryId(long id, long libraryId);

    Optional<BookEntity> findBookByFileNameAndLibraryId(String fileName, long libraryId);

    Optional<BookEntity> findByCurrentHash(String currentHash);

    Optional<BookEntity> findByCurrentHashAndDeletedTrue(String currentHash);

    @Query("SELECT b.id FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    Set<Long> findBookIdsByLibraryId(@Param("libraryId") long libraryId);

    List<BookEntity> findAllByLibraryPathIdAndFileSubPathStartingWith(Long libraryPathId, String fileSubPathPrefix);

    @Query("SELECT b FROM BookEntity b WHERE b.libraryPath.id = :libraryPathId AND b.fileSubPath = :fileSubPath AND b.fileName = :fileName AND (b.deleted IS NULL OR b.deleted = false)")
    Optional<BookEntity> findByLibraryPath_IdAndFileSubPathAndFileName(@Param("libraryPathId") Long libraryPathId,
                                                                       @Param("fileSubPath") String fileSubPath,
                                                                       @Param("fileName") String fileName);

    @Query("SELECT b.id FROM BookEntity b WHERE b.libraryPath.id IN :libraryPathIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<Long> findAllBookIdsByLibraryPathIdIn(@Param("libraryPathIds") Collection<Long> libraryPathIds);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadata();

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query(value = "SELECT b FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false)")
    Page<BookEntity> findAllWithMetadata(Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("bookIds") Set<Long> bookIds);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :bookIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findWithMetadataByIdsWithPagination(@Param("bookIds") Set<Long> bookIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id = :libraryId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryId(@Param("libraryId") Long libraryId);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query(value = "SELECT b FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    Page<BookEntity> findAllWithMetadataByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByShelfId(@Param("shelfId") Long shelfId);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query(value = "SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)",
            countQuery = "SELECT COUNT(DISTINCT b.id) FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    Page<BookEntity> findAllWithMetadataByShelfId(@Param("shelfId") Long shelfId, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "shelves", "libraryPath"})
    @Query("SELECT b FROM BookEntity b WHERE b.fileSizeKb IS NULL AND (b.deleted IS NULL OR b.deleted = false)")
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
                SELECT DISTINCT b FROM BookEntity b
                LEFT JOIN b.metadata m
                LEFT JOIN m.authors a
                WHERE (b.deleted IS NULL OR b.deleted = false) AND (
                      LOWER(m.title) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(m.subtitle) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(m.seriesName) LIKE LOWER(CONCAT('%', :text, '%'))
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :text, '%'))
                )
            """,
            countQuery = """
                        SELECT COUNT(DISTINCT b.id) FROM BookEntity b
                        LEFT JOIN b.metadata m
                        LEFT JOIN m.authors a
                        WHERE (b.deleted IS NULL OR b.deleted = false) AND (
                              LOWER(m.title) LIKE LOWER(CONCAT('%', :text, '%'))
                           OR LOWER(m.subtitle) LIKE LOWER(CONCAT('%', :text, '%'))
                           OR LOWER(m.seriesName) LIKE LOWER(CONCAT('%', :text, '%'))
                           OR LOWER(a.name) LIKE LOWER(CONCAT('%', :text, '%'))
                        )
                    """)
    Page<BookEntity> searchByMetadata(@Param("text") String text, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM BookEntity b WHERE b.deletedAt IS NOT NULL AND b.deletedAt < :cutoff")
    int deleteAllByDeletedAtBefore(Instant cutoff);

    @Modifying
    @Query("""
                UPDATE BookEntity b
                SET b.fileSubPath = :fileSubPath,
                    b.fileName = :fileName,
                    b.library.id = :libraryId,
                    b.libraryPath = :libraryPath
                WHERE b.id = :bookId
            """)
    void updateFileAndLibrary(
            @Param("bookId") Long bookId,
            @Param("fileSubPath") String fileSubPath,
            @Param("fileName") String fileName,
            @Param("libraryId") Long libraryId,
            @Param("libraryPath") LibraryPathEntity libraryPath);
}
