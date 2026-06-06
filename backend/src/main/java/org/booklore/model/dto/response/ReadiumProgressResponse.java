package org.booklore.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadiumProgressResponse {
    private Long bookFileId;
    private Float progressPercent;
    private String readiumLocatorJson;
    private String xpointer;
    private String cfi;
    private String href;
    private Float chapterProgression;
    private String textBefore;
    private String textHighlight;
    private String textAfter;
    private String positionType;
    private Instant lastReadTime;
}
