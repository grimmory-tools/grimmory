package org.booklore.model.dto.response.ranobedbapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.List;
import java.util.Set;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RanobedbBookResponse {
    private Book book;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Book {
        private String description;
        private String lang;
        private Long id;
        private String romaji;
        
        @JsonProperty("description_ja")
        private String descriptionJa;
        
        private Boolean hidden;
        
        @JsonProperty("image_id")
        private Long imageId;
        
        private String olang;
        private Boolean locked;
        
        @JsonProperty("c_release_date")
        private Long cReleaseDate;
        
        private String title;
        
        @JsonProperty("title_orig")
        private String titleOrig;
        
        @JsonProperty("romaji_orig")
        private String romajiOrig;

        private Image image;
        private Rating rating;
        private List<TitleEntry> titles;
        private List<Edition> editions;
        private List<Release> releases;
        private List<Publisher> publishers;
        private Series series;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rating {
        private Double score;
        private Integer count;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        private Long id;
        private String filename;
        private Integer height;
        private Boolean nsfw;
        private Boolean spoiler;
        private Integer width;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TitleEntry {
        private String lang;
        private String romaji;
        @JsonProperty("book_id")
        private Long bookId;
        private Boolean official;
        private String title;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Edition {
        @JsonProperty("book_id")
        private Long bookId;
        private String lang;
        private String title;
        private Long eid;
        private List<Staff> staff;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Staff {
        private String note;
        @JsonProperty("role_type")
        private RoleType roleType;
        private String romaji;
        private String name;
        @JsonProperty("staff_id")
        private Long staffId;
        @JsonProperty("staff_alias_id")
        private Long staffAliasId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Release {
        private String lang;
        private Long id;
        private String romaji;
        private String description;
        private Boolean hidden;
        private Boolean locked;
        @JsonProperty("release_date")
        private Long releaseDate;
        private String title;
        private String website;
        private String amazon;
        private String bookwalker;
        private Format format;
        private String isbn13;
        private Integer pages;
        private String rakuten;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Publisher {
        private String lang;
        private Long id;
        private String romaji;
        private String name;
        @JsonProperty("publisher_type")
        private PublisherType publisherType;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Series {
        private List<SeriesBook> books;
        private List<Tag> tags;
        private String lang;
        private Long id;
        private String romaji;
        private String title;
        @JsonProperty("title_orig")
        private String titleOrig;
        @JsonProperty("romaji_orig")
        private String romajiOrig;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SeriesBook {
        private Long id;
        private String lang;
        private String romaji;
        private String title;
        @JsonProperty("title_orig")
        private String titleOrig;
        @JsonProperty("romaji_orig")
        private String romajiOrig;
        private Image image;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tag {
        private Long id;
        private String name;
        private TagType ttype;
    }

    // --- Enums for strict typing ---

    public enum RoleType {
        @JsonProperty("editor") EDITOR,
        @JsonProperty("staff") STAFF,
        @JsonProperty("author") AUTHOR,
        @JsonProperty("artist") ARTIST,
        @JsonProperty("translator") TRANSLATOR,
        @JsonProperty("narrator") NARRATOR
    }

    public enum Format {
        @JsonProperty("digital") DIGITAL,
        @JsonProperty("print") PRINT,
        @JsonProperty("audio") AUDIO
    }

    public enum PublisherType {
        @JsonProperty("publisher") PUBLISHER,
        @JsonProperty("imprint") IMPRINT
    }

    public enum TagType {
        @JsonProperty("tag") TAG,
        @JsonProperty("content") CONTENT,
        @JsonProperty("demographic") DEMOGRAPHIC,
        @JsonProperty("genre") GENRE
    }
}
