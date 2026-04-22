package org.booklore.model.dto.response.comicvineapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Comic {

    private int id;

    @JsonProperty("api_detail_url")
    private String apiDetailUrl;

    @JsonProperty("cover_date")
    private String coverDate;

    @JsonProperty("store_date")
    private String storeDate;

    private String description;

    private String deck;

    private String name;

    private String aliases;

    @JsonProperty("issue_number")
    private String issueNumber;

    private Image image;

    private Volume volume;

    @JsonProperty("resource_type")
    private String resourceType;

    @JsonProperty("person_credits")
    private List<PersonCredit> personCredits;

    @JsonProperty("character_credits")
    private List<CharacterCredit> characterCredits;

    @JsonProperty("team_credits")
    private List<TeamCredit> teamCredits;

    @JsonProperty("story_arc_credits")
    private List<StoryArcCredit> storyArcCredits;

    @JsonProperty("location_credits")
    private List<LocationCredit> locationCredits;

    @JsonProperty("start_year")
    private String startYear;

    @JsonProperty("count_of_issues")
    private Integer countOfIssues;

    @JsonProperty("site_detail_url")
    private String siteDetailUrl;

    @JsonProperty("first_issue")
    private FirstLastIssue firstIssue;

    @JsonProperty("last_issue")
    private FirstLastIssue lastIssue;

    private Publisher publisher;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Publisher {
        private int id;
        private String name;
        @JsonProperty("api_detail_url")
        private String apiDetailUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Image {
        @JsonProperty("icon_url")
        private String iconUrl;

        @JsonProperty("medium_url")
        private String mediumUrl;

        @JsonProperty("screen_url")
        private String screenUrl;

        @JsonProperty("screen_large_url")
        private String screenLargeUrl;

        @JsonProperty("small_url")
        private String smallUrl;

        @JsonProperty("super_url")
        private String superUrl;

        @JsonProperty("thumb_url")
        private String thumbUrl;

        @JsonProperty("tiny_url")
        private String tinyUrl;

        @JsonProperty("original_url")
        private String originalUrl;

        @JsonProperty("image_tags")
        private String imageTags;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Volume {
        private int id;
        private String name;

        @JsonProperty("api_detail_url")
        private String apiDetailUrl;

        @JsonProperty("site_detail_url")
        private String siteDetailUrl;

        @JsonProperty("start_year")
        private String startYear;

        @JsonProperty("count_of_issues")
        private Integer countOfIssues;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PersonCredit {
        private long id;
        private String name;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CharacterCredit {
        private long id;
        private String name;
        @JsonProperty("api_detail_url")
        private String apiDetailUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamCredit {
        private long id;
        private String name;
        @JsonProperty("api_detail_url")
        private String apiDetailUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StoryArcCredit {
        private long id;
        private String name;
        @JsonProperty("api_detail_url")
        private String apiDetailUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocationCredit {
        private long id;
        private String name;
        @JsonProperty("api_detail_url")
        private String apiDetailUrl;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FirstLastIssue {
        private int id;
        private String name;
        @JsonProperty("api_detail_url")
        private String apiDetailUrl;
        @JsonProperty("issue_number")
        private String issueNumber;
    }
}