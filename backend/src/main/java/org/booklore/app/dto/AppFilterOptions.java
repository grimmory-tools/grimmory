package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.util.List;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppFilterOptions(
        List<CountedOption> authors,
        List<LanguageOption> languages,
        List<CountedOption> readStatuses,
        List<CountedOption> fileTypes,
        List<CountedOption> categories,
        List<CountedOption> publishers,
        List<CountedOption> series,
        List<CountedOption> tags,
        List<CountedOption> moods,
        List<CountedOption> narrators,
        List<CountedOption> ageRatings,
        List<CountedOption> contentRatings,
        List<CountedOption> matchScores,
        List<CountedOption> publishedYears,
        List<CountedOption> fileSizes,
        List<CountedOption> personalRatings,
        List<CountedOption> amazonRatings,
        List<CountedOption> goodreadsRatings,
        List<CountedOption> hardcoverRatings,
        List<CountedOption> lubimyczytacRatings,
        List<CountedOption> ranobedbRatings,
        List<CountedOption> audibleRatings,
        List<CountedOption> pageCounts,
        List<CountedOption> shelfStatuses,
        List<CountedOption> comicCharacters,
        List<CountedOption> comicTeams,
        List<CountedOption> comicLocations,
        List<CountedOption> comicCreators,
        List<CountedOption> shelves,
        List<CountedOption> libraries) {

    public record LanguageOption(String code, String label, long count) {}

    public record CountedOption(String name, long count) {}
}
