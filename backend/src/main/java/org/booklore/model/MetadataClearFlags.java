package org.booklore.model;


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
    private boolean comicvineId;
    private boolean hardcoverId;
    private boolean hardcoverBookId;
    private boolean googleId;
    private boolean pageCount;
    private boolean language;
    private boolean amazonRating;
    private boolean amazonReviewCount;
    private boolean goodreadsRating;
    private boolean goodreadsReviewCount;
    private boolean hardcoverRating;
    private boolean hardcoverReviewCount;
    private boolean lubimyczytacId;
    private boolean lubimyczytacRating;
    private boolean ranobedbId;
    private boolean ranobedbRating;
    private boolean audibleId;
    private boolean audibleRating;
    private boolean audibleReviewCount;
    private boolean authors;
    private boolean categories;
    private boolean moods;
    private boolean tags;
    private boolean cover;
    private boolean audiobookCover;
    private boolean reviews;
    private boolean narrator;
    private boolean abridged;
    private boolean ageRating;
    private boolean contentRating;
}
