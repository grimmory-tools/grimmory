package org.booklore.repository;

import org.booklore.model.entity.BookNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookNoteRepository extends JpaRepository<BookNoteEntity, Long> {

    Optional<BookNoteEntity> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT n FROM BookNoteEntity n WHERE n.bookId = :bookId AND n.userId = :userId ORDER BY n.updatedAt DESC")
    List<BookNoteEntity> findByBookIdAndUserIdOrderByUpdatedAtDesc(@Param("bookId") Long bookId, @Param("userId") Long userId);
}
