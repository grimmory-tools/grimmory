package org.booklore.repository;

import org.booklore.model.entity.UserEmailProviderPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserEmailProviderPreferenceRepository extends JpaRepository<UserEmailProviderPreferenceEntity, Long> {

    Optional<UserEmailProviderPreferenceEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

