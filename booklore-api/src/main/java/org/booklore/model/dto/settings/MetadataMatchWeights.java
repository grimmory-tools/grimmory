package org.booklore.model.dto.settings;


import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataMatchWeights {
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int title = 10;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int subtitle = 1;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int description = 10;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int authors = 10;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int publisher = 5;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int publishedDate = 3;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int seriesName = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int seriesNumber = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int seriesTotal = 1;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int isbn13 = 3;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int isbn10 = 5;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int language = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int pageCount = 1;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int categories = 10;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int amazonRating = 3;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int amazonReviewCount = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int goodreadsRating = 4;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int goodreadsReviewCount = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int hardcoverRating = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int hardcoverReviewCount = 1;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int doubanRating = 3;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int doubanReviewCount = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int ranobedbRating = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int lubimyczytacRating = 2;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int audibleRating = 0;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int audibleReviewCount = 0;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int coverImage = 5;

    public int totalWeight() {
        return title + subtitle + description + authors + publisher + publishedDate +
                seriesName + seriesNumber + seriesTotal + isbn13 + isbn10 + language +
                pageCount + categories + amazonRating + amazonReviewCount +
                goodreadsRating + goodreadsReviewCount + hardcoverRating +
                hardcoverReviewCount + doubanRating + doubanReviewCount +
                ranobedbRating + lubimyczytacRating + audibleRating + audibleReviewCount + coverImage;
    }
}
