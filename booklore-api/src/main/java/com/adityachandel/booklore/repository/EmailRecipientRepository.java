package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.EmailRecipientEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Deprecated
@Repository
public interface EmailRecipientRepository extends JpaRepository<EmailRecipientEntity, Long> {

    Optional<EmailRecipientEntity> findById(long id);

    @Modifying
    @Transactional
    @Query("UPDATE EmailRecipientEntity e SET e.defaultRecipient = false WHERE e.defaultRecipient = true")
    void updateAllRecipientsToNonDefault();

    @Query("SELECT e FROM EmailRecipientEntity e WHERE e.defaultRecipient = true")
    Optional<EmailRecipientEntity> findDefaultEmailRecipient();
}
