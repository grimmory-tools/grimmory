package org.booklore.service.metadata.extractor;

import org.booklore.model.dto.BookMetadata;

import java.io.File;

public interface FileMetadataExtractor {

    BookMetadata extractMetadata(File file);

    byte[] extractCover(File file);
}
