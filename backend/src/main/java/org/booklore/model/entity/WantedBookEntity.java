package org.booklore.model.entity;

import org.booklore.model.enums.WantedBookStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "wanted_book")
public class WantedBookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "author")
    private String author;

    @Column(name = "isbn_13", length = 13)
    private String isbn13;

    @Column(name = "isbn_10", length = 10)
    private String isbn10;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "provider_book_id")
    private String providerBookId;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WantedBookStatus status;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "download_id")
    private String downloadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private BookLoreUserEntity addedBy;

    @Column(name = "added_at")
    private Instant addedAt;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;
}
