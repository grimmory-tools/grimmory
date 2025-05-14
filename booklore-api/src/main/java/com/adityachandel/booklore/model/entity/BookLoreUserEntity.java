package com.adityachandel.booklore.model.entity;

import com.adityachandel.booklore.convertor.BookPreferencesConverter;
import com.adityachandel.booklore.model.dto.settings.BookPreferences;
import com.adityachandel.booklore.model.enums.ProvisioningMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "book_preferences")
    @Convert(converter = BookPreferencesConverter.class)
    private BookPreferences bookPreferences;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserPermissionsEntity permissions;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ShelfEntity> shelves = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "user_library_mapping",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "library_id")
    )
    private List<LibraryEntity> libraries;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}