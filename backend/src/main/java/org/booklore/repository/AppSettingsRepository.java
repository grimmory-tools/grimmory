package org.booklore.repository;

import org.booklore.model.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettingEntity, Long> {
    @QueryHints(@QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "true"))
    AppSettingEntity findByName(String name);
}
