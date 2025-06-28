package com.adityachandel.booklore.service.metadata.upload.extractor;

import com.adityachandel.booklore.model.UploadedFileMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UploadedCbxMetadataExtractor implements UploadedFileMetadataExtractor {

    @Override
    public UploadedFileMetadata extractMetadata(String filePath) {
        UploadedFileMetadata metadata = new UploadedFileMetadata();
        String title = FilenameUtils.getBaseName(filePath);
        metadata.setTitle(title);
        return metadata;
    }
}
