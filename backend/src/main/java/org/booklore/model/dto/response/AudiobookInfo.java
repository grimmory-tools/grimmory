package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudiobookInfo {
    private Long bookId;
    private Long bookFileId;
    private String title;
    private String author;
    private String narrator;
    private Long durationMs;
    private Integer bitrate;
    private String codec;
    private Integer sampleRate;
    private Integer channels;
    private Long totalSizeBytes;
    private boolean folderBased;
    private List<AudiobookChapter> chapters;
    private List<AudiobookTrack> tracks;
}
