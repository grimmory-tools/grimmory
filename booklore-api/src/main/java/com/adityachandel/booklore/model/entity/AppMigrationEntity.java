package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "app_migration")
public class AppMigrationEntity {

    @Id
    @Column(name = "migration_key", nullable = false, unique = true)
    private String key;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "description")
    private String description;
}
