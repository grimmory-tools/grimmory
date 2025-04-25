package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.UploadedFileMetadata;

public interface BookFileMetadataExtractor {

    UploadedFileMetadata extractMetadata(String filePath);
}
