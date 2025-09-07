package com.adityachandel.booklore.service.library;

import com.adityachandel.booklore.model.DuplicateFileInfo;
import com.adityachandel.booklore.model.FileProcessResult;
import com.adityachandel.booklore.model.dto.DuplicateFileNotification;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import com.adityachandel.booklore.model.enums.LibraryScanMode;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.event.BookEventBroadcaster;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessor;
import com.adityachandel.booklore.service.fileprocessor.BookFileProcessorRegistry;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@Component
@Slf4j
public class FileAsBookProcessor implements LibraryFileProcessor {

    private final BookEventBroadcaster bookEventBroadcaster;
    private final BookFileProcessorRegistry processorRegistry;
    private final NotificationService notificationService;

    @Override
    public LibraryScanMode getScanMode() {
        return LibraryScanMode.FILE_AS_BOOK;
    }

    @Override
    @Transactional
    public void processLibraryFiles(List<LibraryFile> libraryFiles, LibraryEntity libraryEntity) {
        for (LibraryFile libraryFile : libraryFiles) {
            log.info("Processing file: {}", libraryFile.getFileName());

            FileProcessResult result = processLibraryFile(libraryFile);

            if (result != null) {
                if (result.getDuplicate() != null) {
                    DuplicateFileInfo dupe = result.getDuplicate();

                    DuplicateFileNotification notification = DuplicateFileNotification.builder()
                            .libraryId(libraryEntity.getId())
                            .libraryName(libraryEntity.getName())
                            .fileId(dupe.getBookId())
                            .fileName(dupe.getFileName())
                            .fullPath(dupe.getFullPath())
                            .hash(dupe.getHash())
                            .timestamp(Instant.now())
                            .build();

                    log.info("Duplicate file detected: {}", notification);

                    notificationService.sendMessage(Topic.DUPLICATE_FILE, notification);
                }

                if (result.getStatus() != FileProcessStatus.DUPLICATE) {
                    bookEventBroadcaster.broadcastBookAddEvent(result.getBook());
                    log.info("Processed file: {}", libraryFile.getFileName());
                }
            }
        }

        log.info("Finished processing library '{}'", libraryEntity.getName());
    }

    @Transactional
    protected FileProcessResult processLibraryFile(LibraryFile libraryFile) {
        BookFileType type = libraryFile.getBookFileType();
        if (type == null) {
            log.warn("Unsupported file type for file: {}", libraryFile.getFileName());
            return null;
        }

        BookFileProcessor processor = processorRegistry.getProcessorOrThrow(type);
        return processor.processFile(libraryFile);
    }

}