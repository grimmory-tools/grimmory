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
        List<CountedOption> narrators) {

    public record LanguageOption(String code, String label, long count) {}

    public record CountedOption(String name, long count) {}
}
