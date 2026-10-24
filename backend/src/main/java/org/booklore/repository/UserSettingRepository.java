package org.booklore.repository;

import org.booklore.model.entity.UserSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserSettingRepository extends JpaRepository<UserSettingEntity, Long> {
    long countBySettingKeyAndSettingValue(String settingKey, String settingValue);
    Optional<UserSettingEntity> findByUserIdAndSettingKey(Long userId, String settingKey);
    void deleteByUserIdAndSettingKey(Long userId, String settingKey);
}
