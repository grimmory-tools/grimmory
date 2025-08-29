package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;

import java.util.List;

import com.adityachandel.booklore.model.enums.LibraryScanMode;

public interface LibraryFileProcessor {
    LibraryScanMode getScanMode();
    void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity);

    /**
     * Indicates whether this processor supports supplementary files (any file type)
     * in addition to book files.
     * @return true if supplementary files are supported, false otherwise
     */
    default boolean supportsSupplementaryFiles() {
        return false;
    }
}