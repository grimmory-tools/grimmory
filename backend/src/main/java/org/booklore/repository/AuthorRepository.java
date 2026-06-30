package org.booklore.repository;

import org.booklore.model.entity.AuthorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AuthorRepository extends JpaRepository<AuthorEntity, Long> {

    Optional<AuthorEntity> findByName(String name);

    Optional<AuthorEntity> findByNameIgnoreCase(String name);

    @Query("SELECT a FROM AuthorEntity a JOIN a.bookMetadataEntityList bm WHERE bm.bookId = :bookId")
    List<AuthorEntity> findAuthorsByBookId(@Param("bookId") Long bookId);

    Optional<AuthorEntity> findByAsin(String asin);

    @Query("SELECT a, COUNT(bm) FROM AuthorEntity a LEFT JOIN a.bookMetadataEntityList bm GROUP BY a ORDER BY a.name")
    List<Object[]> findAllWithBookCount();

    @Query("SELECT a, COUNT(DISTINCT bm) FROM AuthorEntity a LEFT JOIN a.bookMetadataEntityList bm JOIN bm.book b WHERE b.library.id IN :libraryIds GROUP BY a ORDER BY a.name")
    List<Object[]> findAllWithBookCountByLibraryIds(@Param("libraryIds") Set<Long> libraryIds);

    @Query("SELECT COUNT(b) > 0 FROM AuthorEntity a JOIN a.bookMetadataEntityList bm JOIN bm.book b WHERE a.id = :authorId AND b.library.id IN :libraryIds")
    boolean existsByIdAndLibraryIds(@Param("authorId") Long authorId, @Param("libraryIds") Set<Long> libraryIds);

    @Query("SELECT bm.bookId AS bookId, a.name AS authorName FROM AuthorEntity a JOIN a.bookMetadataEntityList bm WHERE bm.bookId IN :bookIds ORDER BY a.name")
    List<AuthorBookProjection> findAuthorNamesByBookIds(@Param("bookIds") Set<Long> bookIds);

    interface AuthorBookProjection {
        Long getBookId();
        String getAuthorName();
    }
}
