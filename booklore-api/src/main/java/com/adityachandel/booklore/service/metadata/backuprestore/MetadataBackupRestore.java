package com.adityachandel.booklore.service.metadata.backuprestore;

import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import org.springframework.core.io.Resource;

import java.io.IOException;

public interface MetadataBackupRestore {

    void backupEmbeddedMetadataIfNotExists(BookEntity bookEntity, boolean backupCover);

    void restoreEmbeddedMetadata(BookEntity bookEntity) throws IOException;

    BookMetadata getBackedUpMetadata(Long bookId);

    Resource getBackupCover(long bookId);

    BookFileType getSupportedBookType();
}
