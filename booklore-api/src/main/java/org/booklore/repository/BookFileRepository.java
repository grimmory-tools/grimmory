package org.booklore.repository;

import org.booklore.model.entity.BookFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookFileRepository extends JpaRepository<BookFileEntity, Long> {

    @Query("""
            SELECT bf FROM BookFileEntity bf
            WHERE bf.book.libraryPath.id = :libraryPathId
            AND bf.fileSubPath = :fileSubPath
            AND bf.fileName = :fileName
            """)
    Optional<BookFileEntity> findByLibraryPathIdAndFileSubPathAndFileName(
            @Param("libraryPathId") Long libraryPathId,
            @Param("fileSubPath") String fileSubPath,
            @Param("fileName") String fileName);

    @Query("SELECT COUNT(bf) FROM BookFileEntity bf WHERE bf.book.id = :bookId")
    long countByBookId(@Param("bookId") Long bookId);

    @Modifying
    @Transactional
    @Query("UPDATE BookFileEntity bf SET bf.book.id = :targetBookId WHERE bf.id IN :fileIds")
    void reassignFilesToBook(@Param("targetBookId") Long targetBookId, @Param("fileIds") List<Long> fileIds);

    @Modifying
    @Transactional
    @Query("UPDATE BookFileEntity bf SET bf.book.id = :targetBookId, bf.fileSubPath = :fileSubPath WHERE bf.id = :fileId")
    void reassignFileToBookWithPath(@Param("targetBookId") Long targetBookId, @Param("fileSubPath") String fileSubPath, @Param("fileId") Long fileId);

    @Query("""
            SELECT bf FROM BookFileEntity bf
            LEFT JOIN FETCH bf.book b
            LEFT JOIN FETCH b.libraryPath
            LEFT JOIN FETCH b.library
            WHERE bf.id = :id
            """)
    Optional<BookFileEntity> findByIdWithBookAndLibraryPath(@Param("id") Long id);
}
