package org.booklore.model.entity;

import org.booklore.model.enums.MetadataProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "public_book_review")
public class BookReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metadata_provider", nullable = false)
    @Enumerated(EnumType.STRING)
    private MetadataProvider metadataProvider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookMetadataEntity bookMetadata;

    @Column(name = "reviewer_name", length = 512)
    private String reviewerName;

    @Column(name = "title", length = 512)
    private String title;

    @Column(name = "rating")
    private Float rating;

    @Column(name = "date")
    private Instant date;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "country", length = 512)
    private String country;

    @Column(name = "spoiler")
    private Boolean spoiler;

    @Column(name = "followers_count")
    private Integer followersCount;

    @Column(name = "text_reviews_count")
    private Integer textReviewsCount;
}
