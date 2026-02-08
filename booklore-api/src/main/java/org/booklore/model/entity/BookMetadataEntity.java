package org.booklore.model.entity;

import org.booklore.util.BookUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "book_metadata")
public class BookMetadataEntity {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "subtitle")
    private String subtitle;

    @Column(name = "publisher")
    private String publisher;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "series_name")
    private String seriesName;

    @Column(name = "series_number")
    private Float seriesNumber;

    @Column(name = "series_total")
    private Integer seriesTotal;

    @Column(name = "isbn_13", length = 13)
    private String isbn13;

    @Column(name = "isbn_10", length = 10)
    private String isbn10;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "language", length = 10)
    private String language;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "cover_updated_on")
    private Instant coverUpdatedOn;

    @Column(name = "audiobook_cover_updated_on")
    private Instant audiobookCoverUpdatedOn;

    @Column(name = "amazon_rating")
    private Double amazonRating;

    @Column(name = "amazon_review_count")
    private Integer amazonReviewCount;

    @Column(name = "goodreads_rating")
    private Double goodreadsRating;

    @Column(name = "goodreads_review_count")
    private Integer goodreadsReviewCount;

    @Column(name = "hardcover_rating")
    private Double hardcoverRating;

    @Column(name = "hardcover_review_count")
    private Integer hardcoverReviewCount;

    @Column(name = "asin", length = 10)
    private String asin;

    @Column(name = "goodreads_id", length = 100)
    private String goodreadsId;

    @Column(name = "hardcover_id", length = 100)
    private String hardcoverId;

    @Column(name = "hardcover_book_id", length = 100)
    private String hardcoverBookId;

    @Column(name = "google_id", length = 100)
    private String googleId;

    @Column(name = "comicvine_id", length = 100)
    private String comicvineId;

    @Column(name = "lubimyczytac_id", length = 100)
    private String lubimyczytacId;

    @Column(name = "lubimyczytac_rating")
    private Double lubimyczytacRating;

    @Column(name = "ranobedb_id", length = 100)
    private String ranobedbId;

    @Column(name = "ranobedb_rating")
    private Double ranobedbRating;

    @Column(name = "audible_id", length = 100)
    private String audibleId;

    @Column(name = "audible_rating")
    private Double audibleRating;

    @Column(name = "audible_review_count")
    private Integer audibleReviewCount;

    @Column(name = "title_locked")
    @Builder.Default
    private Boolean titleLocked = Boolean.FALSE;

    @Column(name = "subtitle_locked")
    @Builder.Default
    private Boolean subtitleLocked = Boolean.FALSE;

    @Column(name = "publisher_locked")
    @Builder.Default
    private Boolean publisherLocked = Boolean.FALSE;

    @Column(name = "published_date_locked")
    @Builder.Default
    private Boolean publishedDateLocked = Boolean.FALSE;

    @Column(name = "description_locked")
    @Builder.Default
    private Boolean descriptionLocked = Boolean.FALSE;

    @Column(name = "isbn_13_locked")
    @Builder.Default
    private Boolean isbn13Locked = Boolean.FALSE;

    @Column(name = "isbn_10_locked")
    @Builder.Default
    private Boolean isbn10Locked = Boolean.FALSE;

    @Column(name = "asin_locked")
    @Builder.Default
    private Boolean asinLocked = Boolean.FALSE;

    @Column(name = "page_count_locked")
    @Builder.Default
    private Boolean pageCountLocked = Boolean.FALSE;

    @Column(name = "language_locked")
    @Builder.Default
    private Boolean languageLocked = Boolean.FALSE;

    @Column(name = "amazon_rating_locked")
    @Builder.Default
    private Boolean amazonRatingLocked = Boolean.FALSE;

    @Column(name = "amazon_review_count_locked")
    @Builder.Default
    private Boolean amazonReviewCountLocked = Boolean.FALSE;

    @Column(name = "goodreads_rating_locked")
    @Builder.Default
    private Boolean goodreadsRatingLocked = Boolean.FALSE;

    @Column(name = "goodreads_review_count_locked")
    @Builder.Default
    private Boolean goodreadsReviewCountLocked = Boolean.FALSE;

    @Column(name = "hardcover_rating_locked")
    @Builder.Default
    private Boolean hardcoverRatingLocked = Boolean.FALSE;

    @Column(name = "hardcover_review_count_locked")
    @Builder.Default
    private Boolean hardcoverReviewCountLocked = Boolean.FALSE;

    @Column(name = "cover_locked")
    @Builder.Default
    private Boolean coverLocked = Boolean.FALSE;

    @Column(name = "audiobook_cover_locked")
    @Builder.Default
    private Boolean audiobookCoverLocked = Boolean.FALSE;

    @Column(name = "series_name_locked")
    @Builder.Default
    private Boolean seriesNameLocked = Boolean.FALSE;

    @Column(name = "series_number_locked")
    @Builder.Default
    private Boolean seriesNumberLocked = Boolean.FALSE;

    @Column(name = "series_total_locked")
    @Builder.Default
    private Boolean seriesTotalLocked = Boolean.FALSE;

    @Column(name = "authors_locked")
    @Builder.Default
    private Boolean authorsLocked = Boolean.FALSE;

    @Column(name = "categories_locked")
    @Builder.Default
    private Boolean categoriesLocked = Boolean.FALSE;

    @Column(name = "moods_locked")
    @Builder.Default
    private Boolean moodsLocked = Boolean.FALSE;

    @Column(name = "tags_locked")
    @Builder.Default
    private Boolean tagsLocked = Boolean.FALSE;

    @Column(name = "goodreads_id_locked")
    @Builder.Default
    private Boolean goodreadsIdLocked = Boolean.FALSE;

    @Column(name = "hardcover_id_locked")
    @Builder.Default
    private Boolean hardcoverIdLocked = Boolean.FALSE;

    @Column(name = "hardcover_book_id_locked")
    @Builder.Default
    private Boolean hardcoverBookIdLocked = Boolean.FALSE;

    @Column(name = "google_id_locked")
    @Builder.Default
    private Boolean googleIdLocked = Boolean.FALSE;

    @Column(name = "comicvine_id_locked")
    @Builder.Default
    private Boolean comicvineIdLocked = Boolean.FALSE;

    @Column(name = "lubimyczytac_id_locked")
    @Builder.Default
    private Boolean lubimyczytacIdLocked = Boolean.FALSE;

    @Column(name = "lubimyczytac_rating_locked")
    @Builder.Default
    private Boolean lubimyczytacRatingLocked = Boolean.FALSE;

    @Column(name = "ranobedb_id_locked")
    @Builder.Default
    private Boolean ranobedbIdLocked = Boolean.FALSE;

    @Column(name = "ranobedb_rating_locked")
    @Builder.Default
    private Boolean ranobedbRatingLocked = Boolean.FALSE;

    @Column(name = "audible_id_locked")
    @Builder.Default
    private Boolean audibleIdLocked = Boolean.FALSE;

    @Column(name = "audible_rating_locked")
    @Builder.Default
    private Boolean audibleRatingLocked = Boolean.FALSE;

    @Column(name = "audible_review_count_locked")
    @Builder.Default
    private Boolean audibleReviewCountLocked = Boolean.FALSE;

    @Column(name = "narrator", length = 500)
    private String narrator;

    @Column(name = "abridged")
    private Boolean abridged;

    @Column(name = "reviews_locked")
    @Builder.Default
    private Boolean reviewsLocked = Boolean.FALSE;

    @Column(name = "narrator_locked")
    @Builder.Default
    private Boolean narratorLocked = Boolean.FALSE;

    @Column(name = "abridged_locked")
    @Builder.Default
    private Boolean abridgedLocked = Boolean.FALSE;

    @Column(name = "embedding_vector", columnDefinition = "TEXT")
    private String embeddingVector;

    @Column(name = "embedding_updated_at")
    private Instant embeddingUpdatedAt;

    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "age_rating")
    private Integer ageRating;

    @Column(name = "content_rating", length = 20)
    private String contentRating;

    @Column(name = "age_rating_locked")
    @Builder.Default
    private Boolean ageRatingLocked = Boolean.FALSE;

    @Column(name = "content_rating_locked")
    @Builder.Default
    private Boolean contentRatingLocked = Boolean.FALSE;

    @PrePersist
    @PreUpdate
    public void updateSearchText() {
        this.searchText = BookUtils.buildSearchText(this);
    }

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "book_id")
    @JsonIgnore
    private BookEntity book;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", referencedColumnName = "book_id", insertable = false, updatable = false)
    private ComicMetadataEntity comicMetadata;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "book_metadata_author_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    @Fetch(FetchMode.SUBSELECT)
    private Set<AuthorEntity> authors;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "book_metadata_category_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Fetch(FetchMode.SUBSELECT)
    private Set<CategoryEntity> categories;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "book_metadata_mood_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "mood_id")
    )
    @Fetch(FetchMode.SUBSELECT)
    private Set<MoodEntity> moods;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "book_metadata_tag_mapping",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Fetch(FetchMode.SUBSELECT)
    private Set<TagEntity> tags;

    @OneToMany(mappedBy = "bookMetadata", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private Set<BookReviewEntity> reviews = new HashSet<>();

    public void applyLockToAllFields(boolean lock) {
        this.titleLocked = lock;
        this.subtitleLocked = lock;
        this.publisherLocked = lock;
        this.publishedDateLocked = lock;
        this.descriptionLocked = lock;
        this.isbn13Locked = lock;
        this.isbn10Locked = lock;
        this.asinLocked = lock;
        this.pageCountLocked = lock;
        this.languageLocked = lock;
        this.coverLocked = lock;
        this.audiobookCoverLocked = lock;
        this.seriesNameLocked = lock;
        this.seriesNumberLocked = lock;
        this.seriesTotalLocked = lock;
        this.authorsLocked = lock;
        this.categoriesLocked = lock;
        this.moodsLocked = lock;
        this.tagsLocked = lock;
        this.amazonRatingLocked = lock;
        this.amazonReviewCountLocked = lock;
        this.goodreadsRatingLocked = lock;
        this.goodreadsReviewCountLocked = lock;
        this.hardcoverRatingLocked = lock;
        this.hardcoverReviewCountLocked = lock;
        this.lubimyczytacRatingLocked = lock;
        this.comicvineIdLocked = lock;
        this.goodreadsIdLocked = lock;
        this.hardcoverIdLocked = lock;
        this.hardcoverBookIdLocked = lock;
        this.googleIdLocked = lock;
        this.lubimyczytacIdLocked = lock;
        this.ranobedbIdLocked = lock;
        this.ranobedbRatingLocked = lock;
        this.audibleIdLocked = lock;
        this.audibleRatingLocked = lock;
        this.audibleReviewCountLocked = lock;
        this.reviewsLocked = lock;
        this.narratorLocked = lock;
        this.abridgedLocked = lock;
        this.ageRatingLocked = lock;
        this.contentRatingLocked = lock;
    }

    public boolean areAllFieldsLocked() {
        return Boolean.TRUE.equals(this.titleLocked)
                && Boolean.TRUE.equals(this.subtitleLocked)
                && Boolean.TRUE.equals(this.publisherLocked)
                && Boolean.TRUE.equals(this.publishedDateLocked)
                && Boolean.TRUE.equals(this.descriptionLocked)
                && Boolean.TRUE.equals(this.isbn13Locked)
                && Boolean.TRUE.equals(this.isbn10Locked)
                && Boolean.TRUE.equals(this.asinLocked)
                && Boolean.TRUE.equals(this.pageCountLocked)
                && Boolean.TRUE.equals(this.languageLocked)
                && Boolean.TRUE.equals(this.coverLocked)
                && Boolean.TRUE.equals(this.audiobookCoverLocked)
                && Boolean.TRUE.equals(this.seriesNameLocked)
                && Boolean.TRUE.equals(this.seriesNumberLocked)
                && Boolean.TRUE.equals(this.seriesTotalLocked)
                && Boolean.TRUE.equals(this.authorsLocked)
                && Boolean.TRUE.equals(this.categoriesLocked)
                && Boolean.TRUE.equals(this.moodsLocked)
                && Boolean.TRUE.equals(this.tagsLocked)
                && Boolean.TRUE.equals(this.amazonRatingLocked)
                && Boolean.TRUE.equals(this.amazonReviewCountLocked)
                && Boolean.TRUE.equals(this.goodreadsRatingLocked)
                && Boolean.TRUE.equals(this.goodreadsReviewCountLocked)
                && Boolean.TRUE.equals(this.hardcoverRatingLocked)
                && Boolean.TRUE.equals(this.hardcoverReviewCountLocked)
                && Boolean.TRUE.equals(this.lubimyczytacRatingLocked)
                && Boolean.TRUE.equals(this.goodreadsIdLocked)
                && Boolean.TRUE.equals(this.comicvineIdLocked)
                && Boolean.TRUE.equals(this.hardcoverIdLocked)
                && Boolean.TRUE.equals(this.hardcoverBookIdLocked)
                && Boolean.TRUE.equals(this.googleIdLocked)
                && Boolean.TRUE.equals(this.lubimyczytacIdLocked)
                && Boolean.TRUE.equals(this.ranobedbIdLocked)
                && Boolean.TRUE.equals(this.ranobedbRatingLocked)
                && Boolean.TRUE.equals(this.audibleIdLocked)
                && Boolean.TRUE.equals(this.audibleRatingLocked)
                && Boolean.TRUE.equals(this.audibleReviewCountLocked)
                && Boolean.TRUE.equals(this.reviewsLocked)
                && Boolean.TRUE.equals(this.narratorLocked)
                && Boolean.TRUE.equals(this.abridgedLocked)
                && Boolean.TRUE.equals(this.ageRatingLocked)
                && Boolean.TRUE.equals(this.contentRatingLocked)
                ;
    }
}
