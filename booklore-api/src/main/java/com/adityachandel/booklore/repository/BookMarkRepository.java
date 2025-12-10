package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.BookMarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookMarkRepository extends JpaRepository<BookMarkEntity, Long> {
    
    Optional<BookMarkEntity> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT b FROM BookMarkEntity b WHERE b.bookId = :bookId AND b.userId = :userId ORDER BY b.createdAt DESC")
    List<BookMarkEntity> findByBookIdAndUserIdOrderByCreatedAtDesc(@Param("bookId") Long bookId, @Param("userId") Long userId);
    
    boolean existsByCfiAndBookIdAndUserId(String cfi, Long bookId, Long userId);
}
