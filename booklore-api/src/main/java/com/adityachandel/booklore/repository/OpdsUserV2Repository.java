package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.OpdsUserV2Entity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpdsUserV2Repository extends JpaRepository<OpdsUserV2Entity, Long> {

    Optional<OpdsUserV2Entity> findByUsername(String username);

    List<OpdsUserV2Entity> findByUserId(Long userId);
}
