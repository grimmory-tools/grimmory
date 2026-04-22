package org.booklore.repository;

import org.booklore.model.entity.JwtSecretEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JwtSecretRepository extends JpaRepository<JwtSecretEntity, Long> {

    @Query("SELECT s.secret FROM JwtSecretEntity s ORDER BY s.createdAt DESC LIMIT 1")
    Optional<String> findLatestSecret();
}
