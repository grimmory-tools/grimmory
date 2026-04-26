package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.model.enums.ContentRestrictionMode;
import org.booklore.model.enums.ContentRestrictionType;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_content_restriction",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "restriction_type", "value"}))
public class UserContentRestrictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(name = "restriction_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ContentRestrictionType restrictionType;

    @Column(name = "mode", nullable = false, length = 15)
    @Enumerated(EnumType.STRING)
    private ContentRestrictionMode mode;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
