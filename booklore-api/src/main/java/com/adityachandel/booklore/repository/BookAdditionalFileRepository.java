package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.model.enums.AdditionalFileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookAdditionalFileRepository extends JpaRepository<BookAdditionalFileEntity, Long> {

    List<BookAdditionalFileEntity> findByBookId(Long bookId);

    List<BookAdditionalFileEntity> findByBookIdAndAdditionalFileType(Long bookId, AdditionalFileType additionalFileType);

    /**
     * Finds a {@link BookAdditionalFileEntity} by its alternative format current hash.
     * <p>
     * This method queries against the {@code alt_format_current_hash} virtual column, which is indexed
     * and only contains values for files where the {@code additional_file_type} is 'ALTERNATIVE_FORMAT'.
     * This implicitly filters by the file type and provides an efficient lookup.
     *
     * @param altFormatCurrentHash The current hash of the file, which is only considered for alternative format files.
     * @return an {@link Optional} containing the found entity, or an empty {@link Optional} if no match is found.
     */
    Optional<BookAdditionalFileEntity> findByAltFormatCurrentHash(String altFormatCurrentHash);

    @Query("SELECT af FROM BookAdditionalFileEntity af WHERE af.book.libraryPath.id = :libraryPathId AND af.fileSubPath = :fileSubPath AND af.fileName = :fileName")
    Optional<BookAdditionalFileEntity> findByLibraryPath_IdAndFileSubPathAndFileName(@Param("libraryPathId") Long libraryPathId,
                                                                                      @Param("fileSubPath") String fileSubPath,
                                                                                      @Param("fileName") String fileName);

    List<BookAdditionalFileEntity> findByAdditionalFileType(AdditionalFileType additionalFileType);

    @Query("SELECT COUNT(af) FROM BookAdditionalFileEntity af WHERE af.book.id = :bookId AND af.additionalFileType = :additionalFileType")
    long countByBookIdAndAdditionalFileType(@Param("bookId") Long bookId, @Param("additionalFileType") AdditionalFileType additionalFileType);

    @Query("SELECT af FROM BookAdditionalFileEntity af WHERE af.book.library.id = :libraryId")
    List<BookAdditionalFileEntity> findByLibraryId(@Param("libraryId") Long libraryId);
}
