package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;

import java.io.File;
import java.io.IOException;

public interface MetadataWriter {

    void saveMetadataToFile(File file, BookMetadataEntity metadata, MetadataClearFlags clearFlags);

    default void saveMetadataToFileStrict(File file, BookMetadataEntity metadata, MetadataClearFlags clearFlags)
            throws IOException {
        throw new IOException("Strict metadata writing is not supported for " + getSupportedBookType());
    }

    boolean shouldSaveMetadataToFile(File file);

    default void replaceCoverImageFromBytes(File bookFile, byte[] contents) {
    }

    BookFileType getSupportedBookType();
}
