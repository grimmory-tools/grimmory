package org.booklore.model.dto.sidecar;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.dto.ComicMetadata;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SidecarBookMetadata {
    private String title;
    private String subtitle;
    private List<String> authors;
    private String publisher;
    private LocalDate publishedDate;
    private String description;
    private String isbn10;
    private String isbn13;
    private String language;
    private Integer pageCount;
    private Set<String> categories;
    private Set<String> moods;
    private Set<String> tags;
    private SidecarSeries series;
    private SidecarIdentifiers identifiers;
    private SidecarRatings ratings;
    private Integer ageRating;
    private String contentRating;
    private String narrator;
    private Boolean abridged;
    private ComicMetadata comicMetadata;
}
