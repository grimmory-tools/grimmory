package com.adityachandel.booklore.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
import java.util.Set;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleBooksApiResponse {
    private List<Item> items;

    @Data
    public static class Item {
        private String id;
        private VolumeInfo volumeInfo;

        @Data
        public static class VolumeInfo {
            private String title;
            private String subtitle;
            private Set<String> authors;
            private String publisher;
            private String publishedDate;
            private String description;
            private List<IndustryIdentifier> industryIdentifiers;
            private Integer pageCount;
            private ImageLinks imageLinks;
            private String language;
            private Set<String> categories;
        }

        @Data
        public static class IndustryIdentifier {
            private String type;
            private String identifier;
        }

        @Data
        public static class ImageLinks {
            private String smallThumbnail;
            private String thumbnail;
            private String small;
            private String medium;
            private String large;
            private String extraLarge;
        }
    }
}