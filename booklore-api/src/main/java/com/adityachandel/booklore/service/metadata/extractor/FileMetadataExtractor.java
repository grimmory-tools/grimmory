package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;

import java.io.File;

public interface FileMetadataExtractor {

    BookMetadata extractMetadata(File file);
}
