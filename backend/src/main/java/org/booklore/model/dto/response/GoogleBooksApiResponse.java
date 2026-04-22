package org.booklore.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleBooksApiResponse {
    private Integer totalItems;
    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String id;
        private String selfLink;
        private VolumeInfo volumeInfo;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class VolumeInfo {
            private String title;
            private String subtitle;
            private List<String> authors;
            private String publisher;
            private String publishedDate;
            private String description;
            private List<IndustryIdentifier> industryIdentifiers;
            private Integer pageCount;
            private Integer printedPageCount;
            private ImageLinks imageLinks;
            private String language;
            private Set<String> categories;
            private Double averageRating;
            private Integer ratingsCount;
            private String maturityRating;
            private String printType;
            private String previewLink;
            private String infoLink;
            private String canonicalVolumeLink;
            private SeriesInfo seriesInfo;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class IndustryIdentifier {
            private String type;
            private String identifier;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ImageLinks {
            private String smallThumbnail;
            private String thumbnail;
            private String small;
            private String medium;
            private String large;
            private String extraLarge;
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SeriesInfo {
            private String kind;
            private String shortSeriesBookTitle;
            private String bookDisplayNumber;
            private List<VolumeSeries> volumeSeries;

            @Data
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class VolumeSeries {
                private String seriesId;
                private String seriesBookType;
                private Integer orderNumber;
            }
        }
    }
}