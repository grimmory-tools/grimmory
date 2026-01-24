package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BookOpdsRepository extends JpaRepository<BookEntity, Long>, JpaSpecificationExecutor<BookEntity> {

    // ============================================
    // ALL BOOKS - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findBookIds(Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "bookFiles", "shelves"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :ids AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIds(@Param("ids") Collection<Long> ids);

    // ============================================
    // RECENT BOOKS - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findRecentBookIds(Pageable pageable);

    // Uses same findAllWithMetadataByIds for second query

    // ============================================
    // BOOKS BY LIBRARY IDs - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findBookIdsByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "bookFiles", "shelves"})
    @Query("SELECT b FROM BookEntity b WHERE b.id IN :ids AND b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIdsAndLibraryIds(@Param("ids") Collection<Long> ids, @Param("libraryIds") Collection<Long> libraryIds);

    // ============================================
    // RECENT BOOKS BY LIBRARY IDs - Two Query Pattern
    // ============================================

    @Query("SELECT b.id FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findRecentBookIdsByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    // Uses findAllWithMetadataByIdsAndLibraryIds for second query

    // ============================================
    // BOOKS BY SHELF ID - Two Query Pattern
    // ============================================

    @Query("SELECT DISTINCT b.id FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findBookIdsByShelfId(@Param("shelfId") Long shelfId, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "bookFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE b.id IN :ids AND s.id = :shelfId AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIdsAndShelfId(@Param("ids") Collection<Long> ids, @Param("shelfId") Long shelfId);

    // ============================================
    // SEARCH BY METADATA - Two Query Pattern
    // ============================================

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE (b.deleted IS NULL OR b.deleted = false) AND (
                  m.searchText LIKE CONCAT('%', :text, '%')
            )
            ORDER BY b.addedOn DESC
            """)
    Page<Long> findBookIdsByMetadataSearch(@Param("text") String text, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "metadata.authors", "metadata.categories", "bookFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b WHERE b.id IN :ids AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithFullMetadataByIds(@Param("ids") Collection<Long> ids);

    // ============================================
    // SEARCH BY METADATA IN LIBRARIES - Two Query Pattern
    // ============================================

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            LEFT JOIN b.metadata m
            WHERE (b.deleted IS NULL OR b.deleted = false)
              AND b.library.id IN :libraryIds
              AND (
                  m.searchText LIKE CONCAT('%', :text, '%')
              )
            ORDER BY b.addedOn DESC
            """)
    Page<Long> findBookIdsByMetadataSearchAndLibraryIds(@Param("text") String text, @Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "metadata.authors", "metadata.categories", "bookFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b WHERE b.id IN :ids AND b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithFullMetadataByIdsAndLibraryIds(@Param("ids") Collection<Long> ids, @Param("libraryIds") Collection<Long> libraryIds);

    // ============================================
    // SEARCH BY METADATA IN SHELVES - Two Query Pattern
    // ============================================

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            LEFT JOIN b.metadata m
            JOIN b.shelves s
            WHERE (b.deleted IS NULL OR b.deleted = false)
              AND s.id IN :shelfIds
              AND (
                  m.searchText LIKE CONCAT('%', :text, '%')
              )
            ORDER BY b.addedOn DESC
            """)
    Page<Long> findBookIdsByMetadataSearchAndShelfIds(@Param("text") String text, @Param("shelfIds") Collection<Long> shelfIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "metadata.authors", "metadata.categories", "bookFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE b.id IN :ids AND s.id IN :shelfIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithFullMetadataByIdsAndShelfIds(@Param("ids") Collection<Long> ids, @Param("shelfIds") Collection<Long> shelfIds);

    // ============================================
    // BOOKS BY SHELF IDs - Two Query Pattern
    // ============================================

    @Query("SELECT DISTINCT b.id FROM BookEntity b JOIN b.shelves s WHERE s.id IN :shelfIds AND (b.deleted IS NULL OR b.deleted = false) ORDER BY b.addedOn DESC")
    Page<Long> findBookIdsByShelfIds(@Param("shelfIds") Collection<Long> shelfIds, Pageable pageable);

    @EntityGraph(attributePaths = {"metadata", "bookFiles", "shelves"})
    @Query("SELECT DISTINCT b FROM BookEntity b JOIN b.shelves s WHERE b.id IN :ids AND s.id IN :shelfIds AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllWithMetadataByIdsAndShelfIds(@Param("ids") Collection<Long> ids, @Param("shelfIds") Collection<Long> shelfIds);

    // ============================================
    // RANDOM BOOKS - "Surprise Me" Feed
    // ============================================

    @Query(value = "SELECT b.id FROM BookEntity b WHERE (b.deleted IS NULL OR b.deleted = false) ORDER BY function('RAND')", nativeQuery = false)
    List<Long> findRandomBookIds();

    @Query(value = "SELECT b.id FROM BookEntity b WHERE b.library.id IN :libraryIds AND (b.deleted IS NULL OR b.deleted = false) ORDER BY function('RAND')", nativeQuery = false)
    List<Long> findRandomBookIdsByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    // ============================================
    // AUTHORS - Distinct Authors List
    // ============================================

    @Query("""
            SELECT DISTINCT a FROM AuthorEntity a
            JOIN a.bookMetadataEntityList m
            JOIN m.book b
            WHERE (b.deleted IS NULL OR b.deleted = false)
            ORDER BY a.name
            """)
    List<com.adityachandel.booklore.model.entity.AuthorEntity> findDistinctAuthors();

    @Query("""
            SELECT DISTINCT a FROM AuthorEntity a
            JOIN a.bookMetadataEntityList m
            JOIN m.book b
            WHERE (b.deleted IS NULL OR b.deleted = false)
              AND b.library.id IN :libraryIds
            ORDER BY a.name
            """)
    List<com.adityachandel.booklore.model.entity.AuthorEntity> findDistinctAuthorsByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    // ============================================
    // BOOKS BY AUTHOR - Two Query Pattern
    // ============================================

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            JOIN b.metadata m
            JOIN m.authors a
            WHERE a.name = :authorName
              AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY b.addedOn DESC
            """)
    Page<Long> findBookIdsByAuthorName(@Param("authorName") String authorName, Pageable pageable);

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            JOIN b.metadata m
            JOIN m.authors a
            WHERE a.name = :authorName
              AND b.library.id IN :libraryIds
              AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY b.addedOn DESC
            """)
    Page<Long> findBookIdsByAuthorNameAndLibraryIds(@Param("authorName") String authorName, @Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);

    // ============================================
    // SERIES - Distinct Series List
    // ============================================

    @Query("""
            SELECT DISTINCT m.seriesName FROM BookMetadataEntity m
            JOIN m.book b
            WHERE (b.deleted IS NULL OR b.deleted = false)
              AND m.seriesName IS NOT NULL
              AND m.seriesName != ''
            ORDER BY m.seriesName
            """)
    List<String> findDistinctSeries();

    @Query("""
            SELECT DISTINCT m.seriesName FROM BookMetadataEntity m
            JOIN m.book b
            WHERE (b.deleted IS NULL OR b.deleted = false)
              AND b.library.id IN :libraryIds
              AND m.seriesName IS NOT NULL
              AND m.seriesName != ''
            ORDER BY m.seriesName
            """)
    List<String> findDistinctSeriesByLibraryIds(@Param("libraryIds") Collection<Long> libraryIds);

    // ============================================
    // BOOKS BY SERIES - Two Query Pattern (sorted by series number)
    // ============================================

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            JOIN b.metadata m
            WHERE m.seriesName = :seriesName
              AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY COALESCE(m.seriesNumber, 999999), b.addedOn DESC
            """)
    Page<Long> findBookIdsBySeriesName(@Param("seriesName") String seriesName, Pageable pageable);

    @Query("""
            SELECT DISTINCT b.id FROM BookEntity b
            JOIN b.metadata m
            WHERE m.seriesName = :seriesName
              AND b.library.id IN :libraryIds
              AND (b.deleted IS NULL OR b.deleted = false)
            ORDER BY COALESCE(m.seriesNumber, 999999), b.addedOn DESC
            """)
    Page<Long> findBookIdsBySeriesNameAndLibraryIds(@Param("seriesName") String seriesName, @Param("libraryIds") Collection<Long> libraryIds, Pageable pageable);
}