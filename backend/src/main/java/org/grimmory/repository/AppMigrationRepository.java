package org.booklore.repository;

import org.booklore.model.entity.AppMigrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppMigrationRepository extends JpaRepository<AppMigrationEntity, String> {
}
