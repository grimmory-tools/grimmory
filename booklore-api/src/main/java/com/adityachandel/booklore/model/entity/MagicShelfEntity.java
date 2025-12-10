package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.model.enums.IconType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "magic_shelf", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "name"})
})
public class MagicShelfEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type", nullable = false)
    @Builder.Default
    private IconType iconType = IconType.PRIME_NG;

    @Column(name = "filter_json", columnDefinition = "json", nullable = false)
    private String filterJson;

    @Column(name = "is_public", nullable = false)
    @lombok.Builder.Default
    private boolean isPublic = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @lombok.Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @lombok.Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void ensureIconType() {
        if (this.iconType == null) {
            this.iconType = IconType.PRIME_NG;
        }
    }
}
