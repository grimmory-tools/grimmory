package com.adityachandel.booklore.model.dto.settings;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataMatchWeights {
    private int title;
    private int subtitle;
    private int description;
    private int authors;
    private int publisher;
    private int publishedDate;
    private int seriesName;
    private int seriesNumber;
    private int seriesTotal;
    private int isbn13;
    private int isbn10;
    private int language;
    private int pageCount;
    private int categories;
    private int amazonRating;
    private int amazonReviewCount;
    private int goodreadsRating;
    private int goodreadsReviewCount;
    private int hardcoverRating;
    private int hardcoverReviewCount;
    private int doubanRating;
    private int doubanReviewCount;
    private int coverImage;

    public int totalWeight() {
        return title + subtitle + description + authors + publisher + publishedDate +
                seriesName + seriesNumber + seriesTotal + isbn13 + isbn10 + language +
                pageCount + categories + amazonRating + amazonReviewCount +
                goodreadsRating + goodreadsReviewCount + hardcoverRating +
                hardcoverReviewCount + doubanRating + doubanReviewCount + coverImage;
    }
}
