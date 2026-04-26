package org.grimmory.service.fileprocessor;

import org.grimmory.model.FileProcessResult;
import org.grimmory.model.dto.settings.LibraryFile;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.enums.BookFileType;

import java.util.List;

import org.grimmory.model.entity.BookFileEntity;

public interface BookFileProcessor {
    List<BookFileType> getSupportedTypes();

    FileProcessResult processFile(LibraryFile libraryFile);

    boolean generateCover(BookEntity bookEntity);

    default boolean generateCover(BookEntity bookEntity, BookFileEntity bookFile) {
        return generateCover(bookEntity);
    }

    default boolean generateAudiobookCover(BookEntity bookEntity) {
        return generateCover(bookEntity);
    }
}
