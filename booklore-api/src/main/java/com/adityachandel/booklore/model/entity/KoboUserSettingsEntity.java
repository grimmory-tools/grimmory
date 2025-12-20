package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "kobo_user_settings")
public class KoboUserSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "token", nullable = false, length = 2048)
    private String token;

    @Column(name = "sync_enabled")
    @Builder.Default
    private boolean syncEnabled = true;

    @Column(name = "progress_mark_as_reading_threshold")
    @Builder.Default
    private Float progressMarkAsReadingThreshold = 1f;

    @Column(name = "progress_mark_as_finished_threshold")
    @Builder.Default
    private Float progressMarkAsFinishedThreshold = 99f;

    @Column(name = "auto_add_to_shelf")
    @Builder.Default
    private boolean autoAddToShelf = false;

    @Column(name = "hardcover_api_key", length = 2048)
    private String hardcoverApiKey;

    @Column(name = "hardcover_sync_enabled")
    @Builder.Default
    private boolean hardcoverSyncEnabled = false;
}
