package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.ShelfEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShelfRepository extends JpaRepository<ShelfEntity, Long> {

    boolean existsByUserIdAndName(Long id, String name);

    List<ShelfEntity> findByUserId(Long id);

    Optional<ShelfEntity> findByUserIdAndName(Long id, String name);

    List<ShelfEntity> findByUserIdInAndName(List<Long> userIds, String name);
}
