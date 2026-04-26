package org.grimmory.task.tasks;

import org.grimmory.exception.ApiError;
import org.grimmory.model.dto.BookLoreUser;
import org.grimmory.model.dto.BookRecommendationLite;
import org.grimmory.model.dto.request.TaskCreateRequest;
import org.grimmory.model.dto.response.TaskCreateResponse;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.model.enums.TaskType;
import org.grimmory.model.enums.UserPermission;
import org.grimmory.model.websocket.TaskProgressPayload;
import org.grimmory.model.websocket.Topic;
import org.grimmory.service.NotificationService;
import org.grimmory.service.book.BookQueryService;
import org.grimmory.service.recommender.BookVectorService;
import org.grimmory.task.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

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
    private static final int BATCH_SIZE = 500;
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

        // Load books in batches, generate embeddings, store lightweight data only
        long totalBooks = bookQueryService.countAllNonDeleted();
        Map<Long, double[]> embeddings = new HashMap<>();
        Map<Long, String> seriesNames = new HashMap<>();

        lastNotificationTime = sendTaskProgressNotification(taskId, 2, String.format("Found %d books, generating embeddings in batches...", totalBooks), TaskStatus.IN_PROGRESS, lastNotificationTime, false);

        int embeddingProgress = 0;
        int batchPage = 0;
        while (true) {
            List<BookEntity> batch = bookQueryService.getAllFullBookEntitiesBatch(
                    PageRequest.of(batchPage, BATCH_SIZE));
            if (batch.isEmpty()) break;

            for (BookEntity book : batch) {
                double[] embedding = vectorService.generateEmbedding(book);
                embeddings.put(book.getId(), embedding);

                // Store series name for similarity filtering
                String series = Optional.ofNullable(book.getMetadata())
                        .map(BookMetadataEntity::getSeriesName)
                        .map(String::toLowerCase)
                        .orElse(null);
                if (series != null) {
                    seriesNames.put(book.getId(), series);
                }

                embeddingProgress++;
            }

            // Save embedding vectors for this batch within a transaction
            Map<Long, String> batchEmbeddingJson = new HashMap<>();
            for (BookEntity book : batch) {
                batchEmbeddingJson.put(book.getId(), vectorService.serializeVector(embeddings.get(book.getId())));
            }
            bookQueryService.compareAndSaveEmbeddings(batchEmbeddingJson);

            int progress = 5 + (int) (embeddingProgress * 30L / totalBooks);
            lastNotificationTime = sendTaskProgressNotification(taskId, progress,
                    String.format("Generated embeddings: %d/%d books", embeddingProgress, totalBooks),
                    TaskStatus.IN_PROGRESS, lastNotificationTime, false);

            if (batch.size() < BATCH_SIZE) break;
            batchPage++;
        }

        lastNotificationTime = sendTaskProgressNotification(taskId, 35, "Computing book similarities...", TaskStatus.IN_PROGRESS, lastNotificationTime, false);

        // Compute similarities using only in-memory vectors (no entities needed)
        Set<Long> allBookIds = embeddings.keySet();
        Map<Long, Set<BookRecommendationLite>> allRecommendations = new HashMap<>();

        int processedBooks = 0;
        for (Map.Entry<Long, double[]> entry : embeddings.entrySet()) {
            Long targetId = entry.getKey();
            try {
                double[] targetVector = entry.getValue();
                if (targetVector == null) continue;

                String targetSeries = seriesNames.get(targetId);

                List<BookVectorService.ScoredBook> candidates = allBookIds.stream()
                        .filter(candidateId -> !candidateId.equals(targetId))
                        .filter(candidateId -> {
                            if (targetSeries == null) return true;
                            String candidateSeries = seriesNames.get(candidateId);
                            return !targetSeries.equals(candidateSeries);
                        })
                        .map(candidateId -> {
                            double[] candidateVector = embeddings.get(candidateId);
                            double similarity = vectorService.cosineSimilarity(targetVector, candidateVector);
                            return new BookVectorService.ScoredBook(candidateId, similarity);
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

                allRecommendations.put(targetId, recommendations);

            } catch (Exception e) {
                log.error("{}: Error computing similarity for book ID {}", getTaskType(), targetId, e);
            }

            processedBooks++;
            if (processedBooks % 10 == 0 || processedBooks == totalBooks) {
                int progress = 35 + (int) (processedBooks * 50L / totalBooks);
                lastNotificationTime = sendTaskProgressNotification(taskId, progress,
                        String.format("Computing similarities: %d/%d books", processedBooks, totalBooks),
                        TaskStatus.IN_PROGRESS, lastNotificationTime, false);
            }
        }

        // Save recommendations in batches
        lastNotificationTime = sendTaskProgressNotification(taskId, 85, String.format("Saving recommendations for %d books...", allRecommendations.size()), TaskStatus.IN_PROGRESS, lastNotificationTime, false);

        bookQueryService.saveRecommendationsInBatches(allRecommendations, BATCH_SIZE);

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
