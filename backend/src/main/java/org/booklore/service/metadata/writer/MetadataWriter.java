package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface MetadataWriter {

    void saveMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clearFlags);

    boolean shouldSaveMetadataToFile(File file);

    default void replaceCoverImageFromUpload(File bookFile, MultipartFile file) {
    }

    default void replaceCoverImageFromBytes(File bookFile, byte[] file) {
    }

    default void replaceCoverImageFromUrl(File bookFile, String url) {
    }

    BookFileType getSupportedBookType();
}
