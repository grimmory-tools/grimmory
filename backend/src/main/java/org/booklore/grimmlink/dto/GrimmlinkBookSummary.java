package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrimmlinkBookSummary {
    private Long bookId;
    private Long bookFileId;
    private String title;
    private String author;
    private String fileName;
    private String originalFileName;
    private String extension;
    private String fileFormat;
    private Long fileSizeKb;
    private Long fileSize;
    private String bookHash;
    private String seriesName;
    private Float seriesNumber;
}
