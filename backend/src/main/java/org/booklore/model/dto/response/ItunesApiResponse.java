package org.booklore.model.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ItunesApiResponse(List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            String wrapperType,
            Long trackId,
            Long collectionId,
            String trackName,
            String collectionName,
            String artistName,
            String description,
            List<String> genres,
            String primaryGenreName,
            String releaseDate,
            String artworkUrl100,
            Double averageUserRating,
            Integer userRatingCount,
            String trackViewUrl,
            String collectionViewUrl,
            String language
    ) {}
}
