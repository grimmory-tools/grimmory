package com.adityachandel.booklore.service.fileprocessor;

import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;

import java.util.List;

public interface BookFileProcessor {
    List<BookFileType> getSupportedTypes();

    FileProcessResult processFile(LibraryFile libraryFile);

    boolean generateCover(BookEntity bookEntity);
}
