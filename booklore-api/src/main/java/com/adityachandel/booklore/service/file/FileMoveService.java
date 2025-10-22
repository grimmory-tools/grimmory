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
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Slf4j
public class FileMoveService {

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
        Set<Long> targetLibraryIds = moves.stream().map(FileMoveRequest.Move::getTargetLibraryId).collect(Collectors.toSet());
        Set<Long> sourceLibraryIds = new HashSet<>();
        monitoringRegistrationService.unregisterLibraries(targetLibraryIds);

        for (FileMoveRequest.Move move : moves) {
            Long bookId = move.getBookId();
            Long targetLibraryId = move.getTargetLibraryId();
            Long targetLibraryPathId = move.getTargetLibraryPathId();

            try {
                Optional<BookEntity> optionalBook = bookRepository.findById(bookId);
                Optional<LibraryEntity> optionalLibrary = libraryRepository.findById(targetLibraryId);
                if (optionalBook.isEmpty() || optionalLibrary.isEmpty()) {
                    continue;
                }
                BookEntity bookEntity = optionalBook.get();
                LibraryEntity targetLibrary = optionalLibrary.get();

                Optional<LibraryPathEntity> optionalLibraryPathEntity = targetLibrary.getLibraryPaths().stream().filter(l -> Objects.equals(l.getId(), targetLibraryPathId)).findFirst();
                if (optionalLibraryPathEntity.isEmpty()) {
                    continue;
                }
                LibraryPathEntity libraryPathEntity = optionalLibraryPathEntity.get();

                LibraryEntity sourceLibrary = bookEntity.getLibrary();
                if (sourceLibrary.getId().equals(targetLibrary.getId())) {
                    continue;
                }
                if (!sourceLibraryIds.contains(sourceLibrary.getId())) {
                    monitoringRegistrationService.unregisterLibraries(Collections.singleton(sourceLibrary.getId()));
                    sourceLibraryIds.add(sourceLibrary.getId());
                }
                Path currentFilePath = bookEntity.getFullFilePath();
                String pattern = fileMoveHelper.getFileNamingPattern(targetLibrary);
                Path newFilePath = fileMoveHelper.generateNewFilePath(bookEntity, libraryPathEntity, pattern);
                if (currentFilePath.equals(newFilePath)) {
                    continue;
                }
                fileMoveHelper.moveFile(currentFilePath, newFilePath);

                String newFileName = newFilePath.getFileName().toString();
                String newFileSubPath = fileMoveHelper.extractSubPath(newFilePath, libraryPathEntity);
                bookRepository.updateFileAndLibrary(bookEntity.getId(), newFileSubPath, newFileName, targetLibrary.getId(), libraryPathEntity);

                Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();
                fileMoveHelper.deleteEmptyParentDirsUpToLibraryFolders(currentFilePath.getParent(), Set.of(libraryRoot));

                entityManager.clear();

                BookEntity fresh = bookRepository.findById(bookId).orElseThrow();

                notificationService.sendMessage(Topic.BOOK_UPDATE, bookMapper.toBookWithDescription(fresh, false));

            } catch (Exception e) {
                log.error("Error moving file for book ID {}: {}", bookId, e.getMessage(), e);
            }
        }

        for (Long libraryId : targetLibraryIds) {
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findById(libraryId);
            optionalLibrary.ifPresent(library -> {
                monitoringRegistrationService.registerLibrary(libraryMapper.toLibrary(library));
            });
        }
        for (Long libraryId : sourceLibraryIds) {
            Optional<LibraryEntity> optionalLibrary = libraryRepository.findById(libraryId);
            optionalLibrary.ifPresent(library -> {
                monitoringRegistrationService.registerLibrary(libraryMapper.toLibrary(library));
            });
        }
    }

    @Transactional
    public FileMoveResult moveSingleFile(BookEntity bookEntity) {

        Long libraryId = bookEntity.getLibraryPath().getLibrary().getId();
        Path libraryRoot = Paths.get(bookEntity.getLibraryPath().getPath()).toAbsolutePath().normalize();

        try {
            String pattern = fileMoveHelper.getFileNamingPattern(bookEntity.getLibraryPath().getLibrary());
            Path currentFilePath = bookEntity.getFullFilePath();
            Path expectedFilePath = fileMoveHelper.generateNewFilePath(bookEntity, bookEntity.getLibraryPath(), pattern);

            if (currentFilePath.equals(expectedFilePath)) {
                return FileMoveResult.builder().moved(false).build();
            }

            log.info("File for book ID {} needs to be moved from {} to {} to match library pattern", bookEntity.getId(), currentFilePath, expectedFilePath);

            fileMoveHelper.unregisterLibrary(libraryId);

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
            fileMoveHelper.registerLibraryPaths(libraryId, libraryRoot);
        }

        return FileMoveResult.builder().moved(false).build();
    }
}
