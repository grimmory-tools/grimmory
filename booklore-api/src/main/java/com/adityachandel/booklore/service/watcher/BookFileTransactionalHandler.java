package com.adityachandel.booklore.service.watcher;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.settings.LibraryFile;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileExtension;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.PermissionType;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.library.LibraryProcessingService;
import com.adityachandel.booklore.util.FileUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.adityachandel.booklore.model.enums.PermissionType.ADMIN;
import static com.adityachandel.booklore.model.enums.PermissionType.MANIPULATE_LIBRARY;
import static com.adityachandel.booklore.model.websocket.LogNotification.createLogNotification;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookFileTransactionalHandler {

    private final BookFilePersistenceService bookFilePersistenceService;
    private final LibraryProcessingService libraryProcessingService;
    private final NotificationService notificationService;
    private final LibraryRepository libraryRepository;

    @Transactional()
    public void handleNewBookFile(long libraryId, Path path, String currentHash) {
        LibraryEntity libraryEntity = libraryRepository.findById(libraryId).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(libraryId));

        Optional<BookEntity> existingOpt = bookFilePersistenceService.findByHash(currentHash);
        if (existingOpt.isPresent()) {
            BookEntity existingBook = existingOpt.get();
            bookFilePersistenceService.updatePathIfChanged(existingBook, libraryEntity, path, currentHash);
            return;
        }

        String filePath = path.toString();
        String fileName = path.getFileName().toString();
        String libraryPath = bookFilePersistenceService.findMatchingLibraryPath(libraryEntity, path);

        notificationService.sendMessageToPermissions(Topic.LOG, createLogNotification("Started processing file: " + filePath), Set.of(ADMIN, MANIPULATE_LIBRARY));

        LibraryPathEntity libraryPathEntity = bookFilePersistenceService.getLibraryPathEntityForFile(libraryEntity, libraryPath);

        LibraryFile libraryFile = LibraryFile.builder()
                .libraryEntity(libraryEntity)
                .libraryPathEntity(libraryPathEntity)
                .fileSubPath(FileUtils.getRelativeSubPath(libraryPathEntity.getPath(), path))
                .fileName(fileName)
                .bookFileType(BookFileExtension.fromFileName(fileName)
                        .map(BookFileExtension::getType)
                        .orElseThrow(() -> new IllegalArgumentException("Unsupported book file type: " + fileName)))
                .build();

        libraryProcessingService.processLibraryFiles(List.of(libraryFile), libraryEntity);

        notificationService.sendMessageToPermissions(Topic.LOG, createLogNotification("Finished processing file: " + filePath), Set.of(ADMIN, MANIPULATE_LIBRARY));
        log.info("[CREATE] Completed processing for file '{}'", filePath);
    }
}
