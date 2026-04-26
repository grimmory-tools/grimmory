package org.grimmory.repository;

import org.grimmory.model.entity.KoboReadingStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KoboReadingStateRepository extends JpaRepository<KoboReadingStateEntity, Long> {
    Optional<KoboReadingStateEntity> findByEntitlementIdAndUserId(String entitlementId, Long userId);
    Optional<KoboReadingStateEntity> findFirstByEntitlementIdAndUserIdIsNullOrderByPriorityTimestampDescLastModifiedStringDescIdDesc(
            String entitlementId);
}