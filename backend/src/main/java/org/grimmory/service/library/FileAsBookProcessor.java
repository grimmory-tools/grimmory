package org.grimmory.service.library;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grimmory.model.dto.settings.LibraryFile;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.util.BookFileGroupingUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Orchestrator for processing library files as books.
 * Delegates transactional processing to BookGroupProcessor.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileAsBookProcessor {

    private final BookGroupProcessor bookGroupProcessor;

    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        Map<String, List<LibraryFile>> groups = BookFileGroupingUtils.groupByBaseName(libraryFiles);
        processLibraryFilesGrouped(groups, libraryEntity);
    }

    public void processLibraryFilesGrouped(Map<String, List<LibraryFile>> groups, LibraryEntity libraryEntity) {
        long libraryId = libraryEntity.getId();
        for (Map.Entry<String, List<LibraryFile>> entry : groups.entrySet()) {
            try {
                bookGroupProcessor.process(entry.getValue(), libraryId);
            } catch (Exception e) {
                String fileNames = entry.getValue().stream()
                        .map(LibraryFile::getFileName).toList().toString();
                log.error("Failed to process file group {}: {}", fileNames, e.getMessage());
            }
        }
        log.info("Finished processing library '{}'", libraryEntity.getName());
    }
}
