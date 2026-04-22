package org.booklore.repository;

import org.booklore.model.entity.CustomFontEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomFontRepository extends JpaRepository<CustomFontEntity, Long> {

    List<CustomFontEntity> findByUserId(Long userId);

    int countByUserId(Long userId);

    Optional<CustomFontEntity> findByIdAndUserId(Long id, Long userId);

}
