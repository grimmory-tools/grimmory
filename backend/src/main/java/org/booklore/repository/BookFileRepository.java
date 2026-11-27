package org.booklore.repository;

import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            SELECT bf FROM BookFileEntity bf
            WHERE bf.book.libraryPath.id = :libraryPathId
            AND bf.fileSubPath = :fileSubPath
            AND bf.fileName = :fileName
            ORDER BY bf.id
            """)
    List<BookFileEntity> findAllByLibraryPathIdAndFileSubPathAndFileName(
            @Param("libraryPathId") Long libraryPathId,
            @Param("fileSubPath") String fileSubPath,
            @Param("fileName") String fileName);

    @Query("SELECT COUNT(bf) FROM BookFileEntity bf WHERE bf.book.id = :bookId")
    long countByBookId(@Param("bookId") Long bookId);

    @Modifying
    @Transactional
    @Query("UPDATE BookFileEntity bf SET bf.book.id = :targetBookId WHERE bf.id IN :fileIds")
    void reassignFilesToBook(@Param("targetBookId") Long targetBookId, @Param("fileIds") List<Long> fileIds);

    @Query("""
            SELECT bf FROM BookFileEntity bf
            LEFT JOIN FETCH bf.book b
            LEFT JOIN FETCH b.libraryPath
            LEFT JOIN FETCH b.library
            WHERE bf.id = :id
            """)
    Optional<BookFileEntity> findByIdWithBookAndLibraryPath(@Param("id") Long id);

    @Query("""
            SELECT COUNT(bf) FROM BookFileEntity bf
            WHERE bf.bookType = :bookType
            AND bf.isBookFormat = true
            AND (bf.book.deleted IS NULL OR bf.book.deleted = false)
            """)
    long countActiveBookFilesByType(@Param("bookType") BookFileType bookType);

    @Query("""
            SELECT bf.id FROM BookFileEntity bf
            WHERE bf.id > :afterId
            AND bf.bookType = :bookType
            AND bf.isBookFormat = true
            AND (bf.book.deleted IS NULL OR bf.book.deleted = false)
            ORDER BY bf.id
            """)
    List<Long> findActiveBookFileIdsByTypeAfter(
            @Param("afterId") Long afterId,
            @Param("bookType") BookFileType bookType,
            Pageable pageable);

    @Query("""
            SELECT bf FROM BookFileEntity bf
            JOIN FETCH bf.book b
            LEFT JOIN FETCH b.libraryPath
            WHERE bf.id IN :ids
            AND bf.bookType = :bookType
            AND bf.isBookFormat = true
            AND (b.deleted IS NULL OR b.deleted = false)
            """)
    List<BookFileEntity> findAllForRepairByIdIn(@Param("ids") List<Long> ids, @Param("bookType") BookFileType bookType);

    @Modifying
    @Transactional
    @Query("UPDATE BookFileEntity bf SET bf.currentHash = :currentHash, bf.fileSizeKb = :fileSizeKb WHERE bf.id = :id")
    int updateFingerprintAndFileSize(
            @Param("id") Long id,
            @Param("currentHash") String currentHash,
            @Param("fileSizeKb") Long fileSizeKb);
}
