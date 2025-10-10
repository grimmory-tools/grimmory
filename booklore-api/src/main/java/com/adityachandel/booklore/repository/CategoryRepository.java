package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {

    Optional<CategoryEntity> findByName(String categoryName);

    Optional<CategoryEntity> findByNameIgnoreCase(String categoryName);
}
