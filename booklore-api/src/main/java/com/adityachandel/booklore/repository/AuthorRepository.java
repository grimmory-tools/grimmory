package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.AuthorEntity;
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

    @Query("SELECT a FROM AuthorEntity a JOIN a.bookMetadataEntityList bm WHERE bm.bookId = :bookId")
    List<AuthorEntity> findAuthorsByBookId(@Param("bookId") Long bookId);
}

