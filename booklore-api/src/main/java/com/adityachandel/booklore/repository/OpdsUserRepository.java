package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.OpdsUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpdsUserRepository extends JpaRepository<OpdsUserEntity, Long> {
    boolean existsByUsername(String username);

    Optional<OpdsUserEntity> findByUsername(String username);
}
