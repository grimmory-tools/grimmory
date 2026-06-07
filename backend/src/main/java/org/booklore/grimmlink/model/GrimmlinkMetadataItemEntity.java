package org.booklore.grimmlink.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(
        name = "grimmlink_metadata_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_grimmlink_metadata_user_book_type_dedupe",
                columnNames = {"user_id", "book_id", "item_type", "dedupe_key"}
        )
)
public class GrimmlinkMetadataItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_file_id")
    private BookFileEntity bookFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    private GrimmlinkMetadataItemType itemType;

    @Column(name = "dedupe_key", nullable = false, length = 191)
    private String dedupeKey;

    @Column(name = "device", length = 100)
    private String device;

    @Column(name = "device_id", length = 191)
    private String deviceId;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "client_updated_at")
    private Instant clientUpdatedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
