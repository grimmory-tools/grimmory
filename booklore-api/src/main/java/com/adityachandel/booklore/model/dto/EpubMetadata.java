package com.adityachandel.booklore.model.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.Set;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpubMetadata {
    private Long bookId;
    private String title;
    private String subtitle;
    private String publisher;
    private LocalDate publishedDate;
    private String description;
    private String seriesName;
    private Float seriesNumber;
    private Integer seriesTotal;
    private String isbn13;
    private String isbn10;
    private Integer pageCount;
    private String language;
    private String asin;
    private Double personalRating;
    private Double amazonRating;
    private Integer amazonReviewCount;
    private String goodreadsId;
    private Double goodreadsRating;
    private Integer goodreadsReviewCount;
    private String hardcoverId;
    private Double hardcoverRating;
    private Integer hardcoverReviewCount;
    private String googleId;
    private Set<String> authors;
    private Set<String> categories;
}
