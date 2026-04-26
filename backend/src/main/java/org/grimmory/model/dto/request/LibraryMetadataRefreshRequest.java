package org.grimmory.model.dto.request;

import org.grimmory.model.enums.MetadataProvider;
import lombok.Data;

@Data
public class LibraryMetadataRefreshRequest {
    private Long libraryId;
    private MetadataProvider metadataProvider;
    private boolean replaceCover;
}
