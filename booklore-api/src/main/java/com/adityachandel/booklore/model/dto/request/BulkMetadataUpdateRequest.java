package com.adityachandel.booklore.model.dto.request;

import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class BulkMetadataUpdateRequest {
    private Set<Long> bookIds;

    private Set<String> authors;
    private boolean clearAuthors;

    private String publisher;
    private boolean clearPublisher;

    private String language;
    private boolean clearLanguage;

    private String seriesName;
    private boolean clearSeriesName;

    private Integer seriesTotal;
    private boolean clearSeriesTotal;

    private LocalDate publishedDate;
    private boolean clearPublishedDate;

    private Set<String> genres;
    private boolean clearGenres;
}
