package org.booklore.opf;

import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;

public interface BookScanMetadataAugmenter {
    void augment(LibraryFile libraryFile, BookEntity bookEntity);
}
