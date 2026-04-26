package org.grimmory.service.library;

import org.grimmory.exception.ApiError;
import org.grimmory.model.MetadataUpdateContext;
import org.grimmory.model.MetadataUpdateWrapper;
import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.LibraryEntity;
import org.grimmory.model.websocket.TaskProgressPayload;
import org.grimmory.model.websocket.Topic;
import org.grimmory.repository.LibraryRepository;
import org.grimmory.repository.BookRepository;
import org.grimmory.service.NotificationService;
import org.grimmory.service.fileprocessor.AudiobookProcessor;
import org.grimmory.service.metadata.BookMetadataUpdater;
import org.grimmory.service.metadata.extractor.MetadataExtractorFactory;
import org.grimmory.task.options.RescanLibraryContext;
import org.grimmory.task.TaskCancellationManager;
import org.grimmory.task.TaskStatus;
import org.grimmory.model.enums.BookFileType;
import org.grimmory.model.enums.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class LibraryRescanHelper {

    private final LibraryRepository libraryRepository;
    private final MetadataExtractorFactory metadataExtractorFactory;
    private final BookMetadataUpdater bookMetadataUpdater;
    private final NotificationService notificationService;
    private final TaskCancellationManager cancellationManager;
    private final BookRepository bookRepository;
    private final AudiobookProcessor audiobookProcessor;

    public LibraryRescanHelper(LibraryRepository libraryRepository, MetadataExtractorFactory metadataExtractorFactory, @Lazy BookMetadataUpdater bookMetadataUpdater, NotificationService notificationService, TaskCancellationManager cancellationManager, BookRepository bookRepository, AudiobookProcessor audiobookProcessor) {
        this.libraryRepository = libraryRepository;
        this.metadataExtractorFactory = metadataExtractorFactory;
        this.bookMetadataUpdater = bookMetadataUpdater;
        this.notificationService = notificationService;
        this.cancellationManager = cancellationManager;
        this.bookRepository = bookRepository;
        this.audiobookProcessor = audiobookProcessor;
    }

    @Transactional
    public void handleRescanOptions(RescanLibraryContext context, String taskId) {

        LibraryEntity library = libraryRepository.findById(context.getLibraryId()).orElseThrow(() -> ApiError.LIBRARY_NOT_FOUND.createException(context.getLibraryId()));

        List<BookEntity> bookEntities = bookRepository.findAllWithMetadataByLibraryId(library.getId());

        log.info("Found {} book(s) to process in library id={}", bookEntities.size(), library.getId());

        int totalBooks = bookEntities.size();
        int processedBooks = 0;

        sendTaskProgressNotification(taskId, 0, String.format("Starting rescan for library: %s", library.getName()), TaskStatus.IN_PROGRESS);

        for (BookEntity bookEntity : bookEntities) {
            if (bookEntity == null || (bookEntity.getDeleted() != null && bookEntity.getDeleted())) {
                continue;
            }

            // Skip fileless books (e.g., physical books) - they have no file to extract metadata from
            if (!bookEntity.hasFiles()) {
                processedBooks++;
                continue;
            }

            if (taskId != null && cancellationManager.isTaskCancelled(taskId)) {
                log.info("Library rescan for library {} was cancelled", library.getId());
                sendTaskProgressNotification(taskId, (processedBooks * 100) / totalBooks,
                        String.format("Rescan cancelled for library: %s (%d/%d books processed)", library.getName(), processedBooks, totalBooks),
                        TaskStatus.CANCELLED);
                break;
            }

            log.info("Processing book: library={}, bookId={}, fileName={}", library.getName(), bookEntity.getId(), bookEntity.getPrimaryBookFile().getFileName());

            int progressPercentage = totalBooks > 0 ? (processedBooks * 100) / totalBooks : 0;

            sendTaskProgressNotification(taskId, progressPercentage,
                    String.format("Processing: %s (Library: %s)", bookEntity.getPrimaryBookFile().getFileName(), library.getName()),
                    TaskStatus.IN_PROGRESS);

            try {
                BookMetadata bookMetadata = metadataExtractorFactory.extractMetadata(bookEntity.getPrimaryBookFile().getBookType(), bookEntity.getFullFilePath().toFile());
                if (bookMetadata == null) {
                    log.warn("No metadata extracted for book id={} path={}", bookEntity.getId(), bookEntity.getFullFilePath());
                    continue;
                }
                MetadataUpdateContext metadataUpdateContext = MetadataUpdateContext.builder()
                        .bookEntity(bookEntity)
                        .metadataUpdateWrapper(
                                MetadataUpdateWrapper.builder()
                                        .metadata(bookMetadata)
                                        .build()
                        )
                        .replaceMode(context.getOptions().getMetadataReplaceMode())
                        .updateThumbnail(false)
                        .mergeCategories(false)
                        .mergeMoods(true)
                        .mergeTags(true)
                        .build();
                bookMetadataUpdater.setBookMetadata(metadataUpdateContext);

                if (bookEntity.getPrimaryBookFile().getBookType() == BookFileType.AUDIOBOOK && bookMetadata.getAudiobookMetadata() != null) {
                    audiobookProcessor.setAudiobookTechnicalMetadata(bookEntity, bookMetadata);
                }
            } catch (Exception e) {
                log.error("Failed to update metadata for book id={} path={}: {}", bookEntity.getId(), bookEntity.getFullFilePath(), e.getMessage(), e);
            } finally {
                processedBooks++;
            }
        }

        if (taskId == null || !cancellationManager.isTaskCancelled(taskId)) {
            sendTaskProgressNotification(taskId, 100,
                    String.format("Rescan completed for library: %s (%d books processed)", library.getName(), processedBooks),
                    TaskStatus.COMPLETED);
        }
    }

    private void sendTaskProgressNotification(String taskId, int progress, String message, TaskStatus taskStatus) {
        try {
            TaskProgressPayload payload = TaskProgressPayload.builder()
                    .taskId(taskId)
                    .taskType(TaskType.REFRESH_LIBRARY_METADATA)
                    .message(message)
                    .progress(progress)
                    .taskStatus(taskStatus)
                    .build();

            notificationService.sendMessage(Topic.TASK_PROGRESS, payload);
        } catch (Exception e) {
            log.error("Failed to send task progress notification for taskId={}: {}", taskId, e.getMessage(), e);
        }
    }
}