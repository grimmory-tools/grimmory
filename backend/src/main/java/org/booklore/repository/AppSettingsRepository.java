package org.booklore.repository;

import org.booklore.model.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettingEntity, Long> {
    AppSettingEntity findByName(String name);
}
