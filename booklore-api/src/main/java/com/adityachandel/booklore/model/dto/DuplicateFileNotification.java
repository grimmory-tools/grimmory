package com.adityachandel.booklore.model.dto;

import lombok.*;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
public class DuplicateFileNotification {
    private long libraryId;
    private String libraryName;
    private long fileId;
    private String fileName;
    private String fullPath;
    private String hash;
    private Instant timestamp;
}
