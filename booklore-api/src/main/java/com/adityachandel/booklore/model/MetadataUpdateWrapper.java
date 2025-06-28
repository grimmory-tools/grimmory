package com.adityachandel.booklore.model;

import com.adityachandel.booklore.model.dto.BookMetadata;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MetadataUpdateWrapper {
    private BookMetadata metadata;
    @Builder.Default
    private MetadataClearFlags clearFlags = new MetadataClearFlags();
}
