package com.adityachandel.booklore.service.metadata.writer;

import com.adityachandel.booklore.model.MetadataClearFlags;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface MetadataWriter {

    void writeMetadataToFile(File file, BookMetadataEntity metadata, String thumbnailUrl, MetadataClearFlags clearFlags);

    default void replaceCoverImageFromUpload(BookEntity bookEntity, MultipartFile file) {
    }

    default void replaceCoverImageFromUrl(BookEntity bookEntity, String url) {
    }

    BookFileType getSupportedBookType();
}
