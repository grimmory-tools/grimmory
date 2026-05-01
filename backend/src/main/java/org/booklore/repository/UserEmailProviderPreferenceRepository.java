package org.booklore.repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

import org.booklore.model.entity.UserEmailProviderPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserEmailProviderPreferenceRepository extends JpaRepository<UserEmailProviderPreferenceEntity, Long> {

    Optional<UserEmailProviderPreferenceEntity> findByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserEmailProviderPreferenceEntity p WHERE p.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}

