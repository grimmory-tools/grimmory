package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;

import java.io.File;

public interface MetadataWriter {

    void saveMetadataToFile(File file, BookMetadataEntity metadata, MetadataClearFlags clearFlags);

    boolean shouldSaveMetadataToFile(File file);

    default void replaceCoverImageFromBytes(File bookFile, byte[] contents) {
    }

    BookFileType getSupportedBookType();
}
