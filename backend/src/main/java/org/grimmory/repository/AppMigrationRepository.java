package org.grimmory.repository;

import org.grimmory.model.entity.AppMigrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppMigrationRepository extends JpaRepository<AppMigrationEntity, String> {
}
