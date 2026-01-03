package com.adityachandel.booklore.task.tasks;

import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.BookRecommendationLite;
import com.adityachandel.booklore.model.dto.request.TaskCreateRequest;
import com.adityachandel.booklore.model.dto.response.TaskCreateResponse;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.enums.TaskType;
import com.adityachandel.booklore.model.enums.UserPermission;
import com.adityachandel.booklore.model.websocket.TaskProgressPayload;
import com.adityachandel.booklore.model.websocket.Topic;
import com.adityachandel.booklore.service.NotificationService;
import com.adityachandel.booklore.service.book.BookQueryService;
import com.adityachandel.booklore.service.recommender.BookVectorService;
import com.adityachandel.booklore.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookRecommendationUpdaterTask implements Task {

    private final BookQueryService bookQueryService;
    private final BookVectorService vectorService;
    private final NotificationService notificationService;

    private static final int RECOMMENDATION_LIMIT = 25;
    private static final long MIN_NOTIFICATION_INTERVAL_MS = 250;

    @Override
    public void validatePermissions(BookLoreUser user, TaskCreateRequest request) {
        if (!UserPermission.CAN_ACCESS_TASK_MANAGER.isGranted(user.getPermissions())) {
            throw ApiError.PERMISSION_DENIED.createException(UserPermission.CAN_ACCESS_TASK_MANAGER);
        }
    }

    @Override
    public TaskCreateResponse execute(TaskCreateRequest request) {
        TaskCreateResponse.TaskCreateResponseBuilder builder = TaskCreateResponse.builder()
                .taskId(request.getTaskId())
                .taskType(TaskType.UPDATE_BOOK_RECOMMENDATIONS);

        String taskId = builder.build().getTaskId();

        long startTime = System.currentTimeMillis();
        log.info("{}: Task started", getTaskType());

        long lastNotificationTime = 0;

        lastNotificationTime = sendTaskProgressNotification(taskId, 0, "Starting book recommendation update", TaskStatus.IN_PROGRESS, lastNotificationTime, true);

        List<BookEntity> allBooks = bookQueryService.getAllFullBookEntities();
        int totalBooks = allBooks.size();

        lastNotificationTime = sendTaskProgressNotification(taskId, 5, String.format("Loaded %d books, generating embeddings...", totalBooks), TaskStatus.IN_PROGRESS, lastNotificationTime, false);

        Map<Long, double[]> embeddings = new HashMap<>();
        List<BookEntity> booksToUpdate = new ArrayList<>();

        int embeddingProgress = 0;
        for (BookEntity book : allBooks) {
            double[] embedding = vectorService.generateEmbedding(book);
            embeddings.put(book.getId(), embedding);

            if (book.getMetadata() != null) {
                String embeddingJson = vectorService.serializeVector(embedding);
                if (!Objects.equals(book.getMetadata().getEmbeddingVector(), embeddingJson)) {
                    book.getMetadata().setEmbeddingVector(embeddingJson);
                    book.getMetadata().setEmbeddingUpdatedAt(Instant.now());
                }
            }

            embeddingProgress++;
            if (embeddingProgress % 10 == 0 || embeddingProgress == totalBooks) {
                int progress = 5 + (embeddingProgress * 30 / totalBooks);
                lastNotificationTime = sendTaskProgressNotification(taskId, progress,
                        String.format("Generated embeddings: %d/%d books", embeddingProgress, totalBooks),
                        TaskStatus.IN_PROGRESS, lastNotificationTime, false);
            }
        }

        lastNotificationTime = sendTaskProgressNotification(taskId, 35, "Computing book similarities...", TaskStatus.IN_PROGRESS, lastNotificationTime, false);

        int processedBooks = 0;
        for (BookEntity targetBook : allBooks) {
            try {
                double[] targetVector = embeddings.get(targetBook.getId());
                if (targetVector == null) continue;

                String targetSeries = Optional.ofNullable(targetBook.getMetadata())
                        .map(BookMetadataEntity::getSeriesName)
                        .map(String::toLowerCase)
                        .orElse(null);

                List<BookVectorService.ScoredBook> candidates = allBooks.stream()
                        .filter(candidate -> !candidate.getId().equals(targetBook.getId()))
                        .filter(candidate -> {
                            String candidateSeries = Optional.ofNullable(candidate.getMetadata())
                                    .map(BookMetadataEntity::getSeriesName)
                                    .map(String::toLowerCase)
                                    .orElse(null);
                            return targetSeries == null || !targetSeries.equals(candidateSeries);
                        })
                        .map(candidate -> {
                            double[] candidateVector = embeddings.get(candidate.getId());
                            double similarity = vectorService.cosineSimilarity(targetVector, candidateVector);
                            return new BookVectorService.ScoredBook(candidate.getId(), similarity);
                        })
                        .filter(scored -> scored.getScore() > 0.1)
                        .collect(Collectors.toList());

                List<BookVectorService.ScoredBook> topSimilar = vectorService.findTopKSimilar(
                        targetVector,
                        candidates,
                        RECOMMENDATION_LIMIT
                );

                Set<BookRecommendationLite> recommendations = topSimilar.stream()
                        .map(scored -> new BookRecommendationLite(scored.getBookId(), scored.getScore()))
                        .collect(Collectors.toSet());

                targetBook.setSimilarBooksJson(recommendations);
                booksToUpdate.add(targetBook);

            } catch (Exception e) {
                log.error("{}: Error updating similar books for book ID {}", getTaskType(), targetBook.getId(), e);
            }

            processedBooks++;
            if (processedBooks % 10 == 0 || processedBooks == totalBooks) {
                int progress = 35 + (processedBooks * 50 / totalBooks);
                lastNotificationTime = sendTaskProgressNotification(taskId, progress,
                        String.format("Computing similarities: %d/%d books", processedBooks, totalBooks),
                        TaskStatus.IN_PROGRESS, lastNotificationTime, false);
            }
        }

        lastNotificationTime = sendTaskProgressNotification(taskId, 85, String.format("Saving recommendations for %d books...", booksToUpdate.size()), TaskStatus.IN_PROGRESS, lastNotificationTime, false);

        bookQueryService.saveAll(booksToUpdate);

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        sendTaskProgressNotification(taskId, 100, String.format("Updated recommendations for %d books in %d ms", totalBooks, endTime - startTime), TaskStatus.COMPLETED, lastNotificationTime, true);

        return builder
                .status(TaskStatus.COMPLETED)
                .build();
    }

    private long sendTaskProgressNotification(String taskId, int progress, String message, TaskStatus taskStatus, long lastNotificationTime, boolean force) {
        long currentTime = System.currentTimeMillis();

        // Send if forced (start/end) or if enough time has passed
        if (force || (currentTime - lastNotificationTime) >= MIN_NOTIFICATION_INTERVAL_MS) {
            try {
                TaskProgressPayload payload = TaskProgressPayload.builder()
                        .taskId(taskId)
                        .taskType(TaskType.UPDATE_BOOK_RECOMMENDATIONS)
                        .message(message)
                        .progress(progress)
                        .taskStatus(taskStatus)
                        .build();

                notificationService.sendMessage(Topic.TASK_PROGRESS, payload);
                return currentTime;
            } catch (Exception e) {
                log.error("Failed to send task progress notification for taskId={}: {}", taskId, e.getMessage(), e);
            }
        }

        return lastNotificationTime;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.UPDATE_BOOK_RECOMMENDATIONS;
    }
}
