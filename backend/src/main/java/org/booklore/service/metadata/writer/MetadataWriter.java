package org.booklore.service.metadata.writer;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;

public interface MetadataWriter {

    void saveMetadataToFile(File file, BookMetadataEntity metadata, MetadataClearFlags clearFlags);

    boolean shouldSaveMetadataToFile(File file);

    default void replaceCoverImageFromPath(BookEntity bookEntity, Path path) {
    }

    BookFileType getSupportedBookType();
}
