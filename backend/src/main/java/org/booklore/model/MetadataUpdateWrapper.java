package org.booklore.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.dto.BookMetadata;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUpdateWrapper {
    private BookMetadata metadata;
    @Builder.Default
    private MetadataClearFlags clearFlags = new MetadataClearFlags();
}
