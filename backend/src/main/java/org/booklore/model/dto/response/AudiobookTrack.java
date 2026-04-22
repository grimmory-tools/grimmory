package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudiobookTrack {
    private Integer index;
    private String fileName;
    private String title;
    private Long durationMs;
    private Long fileSizeBytes;
    private Long cumulativeStartMs;
}
