package com.adityachandel.booklore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class DuplicateFileInfo {
    private final Long bookId;
    private final String fileName;
    private final String fullPath;
    private final String hash;
}
