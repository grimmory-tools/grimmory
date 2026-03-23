package org.booklore.repository;

import org.booklore.model.entity.EmailRecipientV2Entity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailRecipientV2Repository extends JpaRepository<EmailRecipientV2Entity, Long> {

    Optional<EmailRecipientV2Entity> findByIdAndUserId(Long id, Long userId);

    List<EmailRecipientV2Entity> findAllByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE EmailRecipientV2Entity e SET e.defaultRecipient = false WHERE e.defaultRecipient = true AND e.userId = :userId")
    void updateAllRecipientsToNonDefault(Long userId);

    @Query("SELECT e FROM EmailRecipientV2Entity e WHERE e.defaultRecipient = true AND e.userId = :userId")
    Optional<EmailRecipientV2Entity> findDefaultEmailRecipientByUserId(Long userId);
}
