package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findByName(String tagName);

    Optional<TagEntity> findByNameIgnoreCase(String tagName);
}
