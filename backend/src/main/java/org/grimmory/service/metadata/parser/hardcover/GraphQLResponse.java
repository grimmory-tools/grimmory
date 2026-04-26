package org.grimmory.service.metadata.parser.hardcover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphQLResponse {
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Search search;
        private List<BookWithEditions> books;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Search {
        private Results results;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Results {
        @JsonProperty("facet_counts")
        private List<Object> facetCounts;

        private Integer found;
        private List<Hit> hits;

        @JsonProperty("out_of")
        private Integer outOf;

        private Integer page;

        @JsonProperty("request_params")
        private Map<String, Object> requestParams;

        @JsonProperty("search_cutoff")
        private Boolean searchCutoff;

        @JsonProperty("search_time_ms")
        private Integer searchTimeMs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hit {
        private Document document;
        private Map<String, Object> highlight;
        private List<Map<String, Object>> highlights;

        @JsonProperty("text_match")
        private Long textMatch;

        @JsonProperty("text_match_info")
        private Map<String, Object> textMatchInfo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String id;
        private String slug;
        private String title;
        private String subtitle;

        @JsonProperty("author_names")
        private Set<String> authorNames;

        private String description;
        private List<String> isbns;
        private Double rating;

        @JsonProperty("ratings_count")
        private Integer ratingsCount;

        @JsonProperty("reviews_count")
        private Integer reviewsCount;

        private Integer pages;

        @JsonProperty("release_date")
        private String releaseDate;

        @JsonProperty("release_year")
        private Integer releaseYear;

        private List<String> genres;
        private List<String> moods;
        private List<String> tags;

        @JsonProperty("featured_series")
        private FeaturedSeries featuredSeries;

        private Image image;

        @JsonProperty("alternative_titles")
        private List<String> alternativeTitles;

        @JsonProperty("activities_count")
        private Integer activitiesCount;

        private Boolean compilation;

        @JsonProperty("content_warnings")
        private List<String> contentWarnings;

        @JsonProperty("contribution_types")
        private List<String> contributionTypes;

        private List<Map<String, Object>> contributions;

        @JsonProperty("cover_color")
        private String coverColor;

        @JsonProperty("has_audiobook")
        private Boolean hasAudiobook;

        @JsonProperty("has_ebook")
        private Boolean hasEbook;

        @JsonProperty("lists_count")
        private Integer listsCount;

        @JsonProperty("prompts_count")
        private Integer promptsCount;

        @JsonProperty("series_names")
        private List<String> seriesNames;

        @JsonProperty("users_count")
        private Integer usersCount;

        @JsonProperty("users_read_count")
        private Integer usersReadCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BookWithEditions {
        private Integer id;
        private String slug;
        private String title;
        private String subtitle;
        private String description;

        @JsonProperty("cached_contributors")
        private List<Contributor> cachedContributors;

        @JsonProperty("featured_book_series")
        private FeaturedSeries featuredBookSeries;

        private Double rating;

        @JsonProperty("ratings_count")
        private Integer ratingsCount;

        @JsonProperty("reviews_count")
        private Integer reviewsCount;

        private Integer pages;

        @JsonProperty("release_date")
        private String releaseDate;

        @JsonProperty("release_year")
        private Integer releaseYear;

        private Image image;

        @JsonProperty("cached_tags")
        private CachedTags cachedTags;

        private List<Edition> editions;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Edition {
        private Integer id;
        private String title;
        private String subtitle;

        @JsonProperty("cached_contributors")
        private List<Contributor> cachedContributors;

        private Integer pages;

        @JsonProperty("release_date")
        private String releaseDate;

        @JsonProperty("release_year")
        private Integer releaseYear;

        private Image image;

        private Publisher publisher;

        @JsonProperty("isbn_10")
        private String isbn10;

        @JsonProperty("isbn_13")
        private String isbn13;

        private Language language;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        private String url;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FeaturedSeries {
        private Float position;
        private Series series;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Series {
        private String name;
        @JsonProperty("books_count")
        private Integer booksCount;
        @JsonProperty("primary_books_count")
        private Integer primaryBooksCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contributor {
        private Author author;
        private String contribution;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private Integer id;
        private String slug;
        private String name;
        private Image image;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CachedTags {
        @JsonProperty("Genre")
        private List<HardcoverCachedTag> genre;

        @JsonProperty("Mood")
        private List<HardcoverCachedTag> mood;

        @JsonProperty("Tag")
        private List<HardcoverCachedTag> tag;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Publisher {
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Language {
        private String code2;
    }
}
