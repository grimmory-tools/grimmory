package com.adityachandel.booklore.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventTaskType {
    METADATA_REFRESH("Metadata Refresh", true);

    private final String title;
    private final boolean cancellable;
}