package org.booklore.model.entity;

import org.booklore.model.enums.OpdsSortOrder;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "opds_user_v2")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpdsUserV2Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "sort_order", length = 20)
    @Builder.Default
    private OpdsSortOrder sortOrder = OpdsSortOrder.RECENT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
