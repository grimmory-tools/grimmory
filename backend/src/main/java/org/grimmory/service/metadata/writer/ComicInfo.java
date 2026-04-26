package org.booklore.service.metadata.writer;

import jakarta.xml.bind.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@XmlRootElement(name = "ComicInfo")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {
        "title",
        "series",
        "number",
        "count",
        "volume",
        "alternateSeries",
        "alternateNumber",
        "alternateCount",
        "summary",
        "notes",
        "year",
        "month",
        "day",
        "writer",
        "penciller",
        "inker",
        "colorist",
        "letterer",
        "coverArtist",
        "editor",
        "publisher",
        "imprint",
        "genre",
        "tags",
        "web",
        "pageCount",
        "languageISO",
        "format",
        "blackAndWhite",
        "manga",
        "characters",
        "teams",
        "locations",
        "scanInformation",
        "storyArc",
        "storyArcNumber",
        "seriesGroup",
        "ageRating",
        "pages",
        "communityRating",
        "mainCharacterOrTeam",
        "review",
        "gtin"
})
public class ComicInfo {
    @XmlElement(name = "Title")
    private String title;

    @XmlElement(name = "Series")
    private String series;

    @XmlElement(name = "Number")
    private String number;

    @XmlElement(name = "Count")
    private Integer count;

    @XmlElement(name = "Volume")
    private Integer volume;

    @XmlElement(name = "AlternateSeries")
    private String alternateSeries;

    @XmlElement(name = "AlternateNumber")
    private String alternateNumber;

    @XmlElement(name = "AlternateCount")
    private Integer alternateCount;

    @XmlElement(name = "Summary")
    private String summary;

    @XmlElement(name = "Notes")
    private String notes;

    @XmlElement(name = "Year")
    private Integer year;

    @XmlElement(name = "Month")
    private Integer month;

    @XmlElement(name = "Day")
    private Integer day;

    @XmlElement(name = "Writer")
    private String writer;

    @XmlElement(name = "Penciller")
    private String penciller;

    @XmlElement(name = "Inker")
    private String inker;

    @XmlElement(name = "Colorist")
    private String colorist;

    @XmlElement(name = "Letterer")
    private String letterer;

    @XmlElement(name = "CoverArtist")
    private String coverArtist;

    @XmlElement(name = "Editor")
    private String editor;

    @XmlElement(name = "Publisher")
    private String publisher;

    @XmlElement(name = "Imprint")
    private String imprint;

    @XmlElement(name = "Genre")
    private String genre;

    @XmlElement(name = "Tags")
    private String tags;

    @XmlElement(name = "Web")
    private String web;

    @XmlElement(name = "PageCount")
    private Integer pageCount;

    @XmlElement(name = "LanguageISO")
    private String languageISO;

    @XmlElement(name = "Format")
    private String format;

    @XmlElement(name = "BlackAndWhite")
    private String blackAndWhite; // Yes, No, Unknown

    @XmlElement(name = "Manga")
    private String manga; // YesAndRightToLeft, Unknown, No, Yes

    @XmlElement(name = "Characters")
    private String characters;

    @XmlElement(name = "Teams")
    private String teams;

    @XmlElement(name = "Locations")
    private String locations;

    @XmlElement(name = "ScanInformation")
    private String scanInformation;

    @XmlElement(name = "StoryArc")
    private String storyArc;

    @XmlElement(name = "StoryArcNumber")
    private String storyArcNumber;

    @XmlElement(name = "SeriesGroup")
    private String seriesGroup;

    @XmlElement(name = "AgeRating")
    private String ageRating; // Unknown, Adults Only 18+, Early Childhood, Everyone, Everyone 10+, G, Kids to Adults, M, MA15+, Mature 17+, PG, R18+, Rating Pending, T, Teen, X18+

    @XmlElement(name = "Pages")
    private Pages pages;

    @XmlElement(name = "CommunityRating")
    private String communityRating; // 0.0 to 5.0

    @XmlElement(name = "MainCharacterOrTeam")
    private String mainCharacterOrTeam;

    @XmlElement(name = "Review")
    private String review;

    @XmlElement(name = "GTIN")
    private String gtin;

    @Data
    @NoArgsConstructor
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Pages {}
}
