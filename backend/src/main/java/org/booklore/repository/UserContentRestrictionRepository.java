package org.booklore.repository;

import org.booklore.model.entity.UserContentRestrictionEntity;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserContentRestrictionRepository extends JpaRepository<UserContentRestrictionEntity, Long> {

    List<UserContentRestrictionEntity> findByUserId(Long userId);

    List<UserContentRestrictionEntity> findByUserIdAndRestrictionType(Long userId, ContentRestrictionType type);

    List<UserContentRestrictionEntity> findByUserIdAndMode(Long userId, ContentRestrictionMode mode);

    Optional<UserContentRestrictionEntity> findByUserIdAndRestrictionTypeAndValue(
            Long userId, ContentRestrictionType restrictionType, String value);

    void deleteByUserId(Long userId);

    void deleteByUserIdAndRestrictionType(Long userId, ContentRestrictionType type);

    @Query("SELECT r FROM UserContentRestrictionEntity r WHERE r.user.id = :userId AND r.restrictionType = :type AND r.mode = :mode")
    List<UserContentRestrictionEntity> findByUserIdAndTypeAndMode(
            @Param("userId") Long userId,
            @Param("type") ContentRestrictionType type,
            @Param("mode") ContentRestrictionMode mode);

    boolean existsByUserIdAndRestrictionTypeAndValue(Long userId, ContentRestrictionType type, String value);
}
