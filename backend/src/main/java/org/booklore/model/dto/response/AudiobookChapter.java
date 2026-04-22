package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudiobookChapter {
    private Integer index;
    private String title;
    private Long startTimeMs;
    private Long endTimeMs;
    private Long durationMs;
}
