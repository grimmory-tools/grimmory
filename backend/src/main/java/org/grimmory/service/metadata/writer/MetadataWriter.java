package org.grimmory.service.metadata.writer;

import org.grimmory.model.MetadataClearFlags;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.model.enums.BookFileType;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface MetadataWriter {

    void saveMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clearFlags);

    boolean shouldSaveMetadataToFile(File file);

    default void replaceCoverImageFromUpload(BookEntity bookEntity, MultipartFile file) {
    }

    default void replaceCoverImageFromBytes(BookEntity bookEntity, byte[] file) {
    }

    default void replaceCoverImageFromUrl(BookEntity bookEntity, String url) {
    }

    BookFileType getSupportedBookType();
}
