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
    private boolean syncEnabled = true;

    @Column(name = "progress_mark_as_reading_threshold")
    private Float progressMarkAsReadingThreshold = 1f;

    @Column(name = "progress_mark_as_finished_threshold")
    private Float progressMarkAsFinishedThreshold = 99f;
}
