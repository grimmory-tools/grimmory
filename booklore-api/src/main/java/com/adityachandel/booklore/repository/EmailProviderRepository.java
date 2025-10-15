package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.EmailProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Deprecated
@Repository
public interface EmailProviderRepository extends JpaRepository<EmailProviderEntity, Long> {

    @Modifying
    @Query("UPDATE EmailProviderEntity e SET e.defaultProvider = false")
    void updateAllProvidersToNonDefault();

    @Query("SELECT e FROM EmailProviderEntity e WHERE e.defaultProvider = true")
    Optional<EmailProviderEntity> findDefaultEmailProvider();
}
