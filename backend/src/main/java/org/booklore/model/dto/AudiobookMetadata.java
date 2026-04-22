package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudiobookMetadata {
    private Long durationSeconds;
    private Integer bitrate;
    private Integer sampleRate;
    private Integer channels;
    private String codec;
    private Integer chapterCount;
    private List<ChapterInfo> chapters;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChapterInfo {
        private Integer index;
        private String title;
        private Long startTimeMs;
        private Long endTimeMs;
        private Long durationMs;
    }
}
