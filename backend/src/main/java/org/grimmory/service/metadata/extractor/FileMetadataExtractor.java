package org.grimmory.service.metadata.extractor;

import org.grimmory.model.dto.BookMetadata;

import java.io.File;

public interface FileMetadataExtractor {

    BookMetadata extractMetadata(File file);

    byte[] extractCover(File file);
}
