package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.MoodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MoodRepository extends JpaRepository<MoodEntity, Long> {

    Optional<MoodEntity> findByName(String moodName);

    Optional<MoodEntity> findByNameIgnoreCase(String moodName);
}
