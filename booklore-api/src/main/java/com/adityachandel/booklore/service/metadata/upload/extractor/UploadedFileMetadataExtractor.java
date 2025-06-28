package com.adityachandel.booklore.service.metadata.upload.extractor;

import com.adityachandel.booklore.model.UploadedFileMetadata;

public interface UploadedFileMetadataExtractor {

    UploadedFileMetadata extractMetadata(String filePath);
}
