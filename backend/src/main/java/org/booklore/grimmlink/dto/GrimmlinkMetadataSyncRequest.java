package org.booklore.grimmlink.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class GrimmlinkMetadataSyncRequest {
    private Integer schemaVersion;
    private String syncMode;
    private Long bookId;
    private String bookHash;
    private Long bookFileId;
    private String fileFormat;
    private String device;
    @JsonAlias("device_id")
    private String deviceId;
    private Instant timestamp;
    private Instant since;
    private Integer limit;
    private String type;
    private GrimmlinkRatingPayload rating;
    private List<GrimmlinkAnnotationPayload> annotations = new ArrayList<>();
    private List<GrimmlinkBookmarkPayload> bookmarks = new ArrayList<>();
}
