package org.booklore.repository;

import org.booklore.model.entity.EmailProviderV2Entity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailProviderV2Repository extends JpaRepository<EmailProviderV2Entity, Long> {

    Optional<EmailProviderV2Entity> findByIdAndUserId(Long id, Long userId);

    List<EmailProviderV2Entity> findAllByUserId(Long userId);

    @Query("SELECT e FROM EmailProviderV2Entity e WHERE e.shared = true AND e.userId IN (SELECT u.id FROM BookLoreUserEntity u WHERE u.permissions.permissionAdmin = true)")
    List<EmailProviderV2Entity> findAllBySharedTrueAndAdmin();

    @Query("SELECT e FROM EmailProviderV2Entity e WHERE e.id = :id AND e.shared = true AND e.userId IN (SELECT u.id FROM BookLoreUserEntity u WHERE u.permissions.permissionAdmin = true)")
    Optional<EmailProviderV2Entity> findSharedProviderById(@Param("id") Long id);

    @Query("SELECT e FROM EmailProviderV2Entity e WHERE e.id = :id AND (e.userId = :userId OR (e.shared = true AND e.userId IN (SELECT u.id FROM BookLoreUserEntity u WHERE u.permissions.permissionAdmin = true)))")
    Optional<EmailProviderV2Entity> findAccessibleProvider(@Param("id") Long id, @Param("userId") Long userId);
}
