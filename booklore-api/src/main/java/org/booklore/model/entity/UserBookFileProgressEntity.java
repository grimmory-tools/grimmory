package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_book_file_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "book_file_id"}))
public class UserBookFileProgressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_file_id", nullable = false)
    private BookFileEntity bookFile;

    @Column(name = "position_data", length = 1000)
    private String positionData;

    @Column(name = "position_href", length = 1000)
    private String positionHref;

    @Column(name = "progress_percent")
    private Float progressPercent;

    @Column(name = "tts_position_cfi", length = 1000)
    private String ttsPositionCfi;

    @Column(name = "last_read_time")
    private Instant lastReadTime;
}
