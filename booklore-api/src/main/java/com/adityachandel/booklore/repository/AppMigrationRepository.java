package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.AppMigrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppMigrationRepository extends JpaRepository<AppMigrationEntity, String> {
}
