package org.grimmory.service.metadata.parser;

import org.grimmory.model.dto.BookMetadata;

public interface DetailedMetadataProvider {
    BookMetadata fetchDetailedMetadata(String providerItemId);
}
