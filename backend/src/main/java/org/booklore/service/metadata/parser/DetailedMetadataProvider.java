package org.booklore.service.metadata.parser;

import org.booklore.model.dto.BookMetadata;

public interface DetailedMetadataProvider {
    BookMetadata fetchDetailedMetadata(String providerItemId);
}
