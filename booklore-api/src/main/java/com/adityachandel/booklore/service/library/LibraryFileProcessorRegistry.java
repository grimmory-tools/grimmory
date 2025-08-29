package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LibraryFileProcessorRegistry {

    private final Map<LibraryScanMode, LibraryFileProcessor> processorMap;

    public LibraryFileProcessorRegistry(List<LibraryFileProcessor> processors) {
        this.processorMap = processors.stream()
                .collect(Collectors.toMap(LibraryFileProcessor::getScanMode, Function.identity()));
    }

    public LibraryFileProcessor getProcessor(LibraryEntity libraryEntity) {
        return processorMap.get(libraryEntity.getScanMode());
    }
}
