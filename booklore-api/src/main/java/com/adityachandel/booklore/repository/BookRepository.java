package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long>, JpaSpecificationExecutor<BookEntity> {

    List<BookEntity> findAllByIdIn(Collection<Long> ids);

    List<BookEntity> findBooksByLibraryId(Long libraryId);

    @Query("SELECT b.id FROM BookEntity b WHERE b.libraryPath.id IN :libraryPathIds")
    List<Long> findAllBookIdsByLibraryPathIdIn(@Param("libraryPathIds") Collection<Long> libraryPathIds);

    Optional<BookEntity> findBookByIdAndLibraryId(long id, long libraryId);

    Optional<BookEntity> findBookByFileNameAndLibraryId(String fileName, long libraryId);

    @Query("SELECT b FROM BookEntity b JOIN b.shelves s WHERE s.id = :shelfId")
    List<BookEntity> findByShelfId(@Param("shelfId") Long shelfId);

    @Modifying
    @Query("DELETE FROM BookEntity b WHERE b.id IN (:ids)")
    void deleteByIdIn(Collection<Long> ids);

    List<BookEntity> findByLibraryIdIn(Set<Long> userLibraryIds);

    List<BookEntity> findByFileSizeKbIsNull();
}

