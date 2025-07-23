package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.entity.LibraryEntity;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

// Removed unused imports
@AllArgsConstructor
@Component
public class LibraryFileProcessorRegistry {

    private final FileAsBookProcessor fileAsBookProcessor;

    public LibraryFileProcessor getProcessor(LibraryEntity library) {
        return fileAsBookProcessor;
    }
}
