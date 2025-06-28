package com.adityachandel.booklore.model;


import lombok.Data;

@Data
public class MetadataClearFlags {
    private boolean title;
    private boolean subtitle;
    private boolean publisher;
    private boolean publishedDate;
    private boolean description;
    private boolean seriesName;
    private boolean seriesNumber;
    private boolean seriesTotal;
    private boolean isbn13;
    private boolean isbn10;
    private boolean asin;
    private boolean goodreadsId;
    private boolean hardcoverId;
    private boolean googleId;
    private boolean pageCount;
    private boolean language;
    private boolean amazonRating;
    private boolean amazonReviewCount;
    private boolean goodreadsRating;
    private boolean goodreadsReviewCount;
    private boolean hardcoverRating;
    private boolean hardcoverReviewCount;
    private boolean personalRating;
    private boolean authors;
    private boolean categories;
    private boolean cover;
}
