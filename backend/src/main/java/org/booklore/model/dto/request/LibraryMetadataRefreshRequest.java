package org.booklore.model.dto.request;

import org.booklore.model.enums.MetadataProvider;
import lombok.Data;

@Data
public class LibraryMetadataRefreshRequest {
    private Long libraryId;
    private MetadataProvider metadataProvider;
    private boolean replaceCover;
}
