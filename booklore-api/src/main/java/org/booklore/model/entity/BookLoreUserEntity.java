package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.model.enums.ProvisioningMethod;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "users")
public class BookLoreUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "is_default_password", nullable = false)
    private boolean isDefaultPassword;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    @Column(name = "provisioning_method")
    @Enumerated(EnumType.STRING)
    private ProvisioningMethod provisioningMethod;

    @Column(name = "oidc_subject")
    private String oidcSubject;

    @Column(name = "oidc_issuer", length = 512)
    private String oidcIssuer;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserPermissionsEntity permissions;

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ShelfEntity> shelves = new HashSet<>();

    @BatchSize(size = 20)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_library_mapping",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "library_id")
    )
    @Builder.Default
    private Set<LibraryEntity> libraries = new HashSet<>();

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserSettingEntity> settings = new HashSet<>();

    @OneToOne(mappedBy = "bookLoreUser", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private KoreaderUserEntity koreaderUser;

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<ReadingSessionEntity> readingSessions = new HashSet<>();

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserContentRestrictionEntity> contentRestrictions = new HashSet<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}