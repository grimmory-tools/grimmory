package org.grimmory.repository;

import org.grimmory.model.entity.MagicShelfEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MagicShelfRepository extends JpaRepository<MagicShelfEntity, Long> {

    List<MagicShelfEntity> findAllByUserId(Long userId);

    List<MagicShelfEntity> findAllByIsPublicIsTrue();

    Optional<MagicShelfEntity> findByUserIdAndName(Long userId, String name);

    boolean existsByUserIdAndName(Long userId, String name);
}
