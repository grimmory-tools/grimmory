package com.adityachandel.booklore.service.file;

import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.FileMoveResult;
import com.adityachandel.booklore.model.dto.request.FileMoveRequest;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.repository.LibraryRepository;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.monitoring.MonitoringRegistrationService;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@AllArgsConstructor
@Service
@Slf4j
public class FileMoveService {

    private static final long EVENT_DRAIN_TIMEOUT_MS = 300;

    private final BookRepository bookRepository;
    private final LibraryRepository libraryRepository;
    private final FileMoveHelper fileMoveHelper;
    private final MonitoringRegistrationService monitoringRegistrationService;
    private final LibraryMapper libraryMapper;
    private final BookMapper bookMapper;
    private final NotificationService notificationService;
    private final EntityManager entityManager;


    @Transactional
    public void bulkMoveFiles(FileMoveRequest request) {
        List<FileMoveRequest.Move> moves = request.getMoves();
        
        Set<Long> allAffectedLibraryIds = collectAllAffectedLibraryIds(moves);
        Set<Path> libraryPaths = monitoringRegistrationService.getPathsForLibraries(allAffectedLibraryIds);
        
        log.info("Unregistering {} libraries before bulk file move", allAffectedLibraryIds.size());
        monitoringRegistrationService.unregisterLibraries(allAffectedLibraryIds);
        monitoringRegistrationService.waitForEventsDrainedByPaths(libraryPaths, EVENT_DRAIN_TIMEOUT_MS);

        try {
            for (FileMoveRequest.Move move : moves) {
                processSingleMove(move);
            }
        } finally {
            for (Long libraryId : allAffectedLibraryIds) {
                libraryRepository.findById(libraryId)
                        .ifPresent(library -> monitoringRegistrationService.registerLibrary(libraryMapper.toLibrary(library)));
            }
        }
    }

    private Set<Long> collectAllAffectedLibraryIds(List<FileMoveRequest.Move> moves) {
        Set<Long> libraryIds = new HashSet<>();
        
        for (FileMoveRequest.Move move : moves) {
            libraryIds.add(move.getTargetLibraryId());
            bookRepository.findById(move.getBookId())
                    .ifPresent(book -> libraryIds.add(book.getLibrary().getId()));
        }
        
        return libraryIds;
    }

    private void processSingleMove(FileMoveRequest.Move move) {
        Long bookId = move.getBookId();
        Long targetLibraryId = move.getTargetLibraryId();
        Long targetLibraryPathId = move.getTargetLibraryPathId();

        Path tempPath = null;
        Path currentFilePath = null;

        try {
            Optional<BookEntity> optionalBook = bookRepository.findById(bookId);
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findById(targetLibraryId);
            if (optionalBook.isEmpty()) {
                log.warn("Book not found for move operation: bookId={}", bookId);
                return;
            }
            if (optionalLibrary.isEmpty()) {
                log.warn("Target library not found for move operation: libraryId={}", targetLibraryId);
                return;
            }
            BookEntity bookEntity = optionalBook.get();
            LibraryEntity targetLibrary = optionalLibrary.get();

            Optional<LibraryPathEntity> optionalLibraryPathEntity = targetLibrary.getLibraryPaths().stream()
                    .filter(libraryPath -> Objects.equals(libraryPath.getId(), targetLibraryPathId))
                    .findFirst();
            if (optionalLibraryPathEntity.isEmpty()) {
                log.warn("Target library path not found for move operation: libraryId={}, pathId={}", targetLibraryId, targetLibraryPathId);
                return;
            }
            LibraryPathEntity libraryPathEntity = optionalLibraryPathEntity.get();

            currentFilePath = bookEntity.getFullFilePath();
            String pattern = fileMoveHelper.getFileNamingPattern(targetLibrary);
            Path newFilePath = fileMoveHelper.generateNewFilePath(bookEntity, libraryPathEntity, pattern);
            if (currentFilePath.equals(newFilePath)) {
                return;
            }

            tempPath = fileMoveHelper.moveFileWithBackup(currentFilePath);

            String newFileName = newFilePath.getFileName().toString();
            String newFileSubPath = fileMoveHelper.extractSubPath(newFilePath, libraryPathEntity);
            bookRepository.updateFileAndLibrary(bookEntity.getId(), newFileSubPath, newFileName, targetLibrary.getId(), libraryPathEntity);

            fileMoveHelper.commitMove(tempPath, newFilePath);
            tempPath = null;

            Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(currentFilePath.getParent(), Set.of(libraryRoot));

            entityManager.clear();

            BookEntity fresh = bookRepository.findById(bookId).orElseThrow();

            notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));

        } catch (Exception e) {
            log.error("Error moving file for book ID {}: {}", bookId, e.getMessage(), e);
        } finally {
            if (tempPath != null && currentFilePath != null) {
                fileMoveHelper.rollbackMove(tempPath, currentFilePath);
            }
        }
    }

    @Transactional
    public FileMoveResult moveSingleFile(BookEntity bookEntity) {

        Long libraryId = bookEntity.getLibraryPath().getLibrary().getId();
        Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
        boolean isLibraryMonitoredWhenCalled = false;

        try {
            isLibraryMonitoredWhenCalled = monitoringRegistrationService.isLibraryMonitored(libraryId);
            String pattern = fileMoveHelper.getFileNamingPattern(bookEntity.getLibraryPath().getLibrary());
            Path currentFilePath = bookEntity.getFullFilePath();
            Path expectedFilePath = fileMoveHelper.generateNewFilePath(bookEntity, bookEntity.getLibraryPath(), pattern);

            if (currentFilePath.equals(expectedFilePath)) {
                return FileMoveResult.builder().moved(false).build();
            }

            log.info("File for book ID {} needs to be moved from {} to {} to match library pattern", bookEntity.getId(), currentFilePath, expectedFilePath);

            if (isLibraryMonitoredWhenCalled) {
                log.debug("Unregistering library {} before moving a single file", libraryId);
                Set<Path> libraryPaths = monitoringRegistrationService.getPathsForLibraries(Set.of(libraryId));
                fileMoveHelper.unregisterLibrary(libraryId);
                monitoringRegistrationService.waitForEventsDrainedByPaths(libraryPaths, EVENT_DRAIN_TIMEOUT_MS);
            }

            fileMoveHelper.moveFile(currentFilePath, expectedFilePath);

            fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(currentFilePath.getParent(), Set.of(libraryRoot));

            String newFileName = expectedFilePath.getFileName().toString();
            String newFileSubPath = fileMoveHelper.extractSubPath(expectedFilePath, bookEntity.getLibraryPath());

            return FileMoveResult.builder()
                    .moved(true)
                    .newFileName(newFileName)
                    .newFileSubPath(newFileSubPath)
                    .build();
        } catch (Exception e) {
            log.error("Failed to move file for book ID {}: {}", bookEntity.getId(), e.getMessage(), e);
        } finally {
            if (isLibraryMonitoredWhenCalled) {
                log.debug("Registering library paths for library {} with root {}", libraryId, libraryRoot);
                fileMoveHelper.registerLibraryPaths(libraryId, libraryRoot);
            }
        }

        return FileMoveResult.builder().moved(false).build();
    }
}
