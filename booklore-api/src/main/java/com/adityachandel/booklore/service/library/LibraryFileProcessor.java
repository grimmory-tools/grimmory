package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;

import java.util.List;

public interface LibraryFileProcessor {
    void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity);
}