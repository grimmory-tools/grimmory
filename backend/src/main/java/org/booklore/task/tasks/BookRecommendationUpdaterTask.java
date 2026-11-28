package org.booklore.task.tasks;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.BookRecommendationLite;
import org.booklore.model.dto.request.TaskCreateRequest;
import org.booklore.model.dto.response.TaskCreateResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.TaskType;
import org.booklore.model.enums.UserPermission;
import org.booklore.model.websocket.TaskProgressPayload;
import org.booklore.model.websocket.Topic;
import org.booklore.service.NotificationService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.recommender.BookVectorService;
import org.booklore.service.task.TaskHistoryService;
import org.booklore.task.TaskStatus;
import org.booklore.task.TaskCancellationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookRecommendationUpdaterTask implements Task {

    private final BookQueryService bookQueryService;
    private final BookVectorService vectorService;
    private final NotificationService notificationService;
    private final TaskHistoryService taskHistoryService;
    private final TaskCancellationManager cancellationManager;

    private static final int RECOMMENDATION_CANDIDATE_POOL_SIZE = 100;
    private static final int BATCH_SIZE = 500;
    private static final long MIN_NOTIFICATION_INTERVAL_MS = 250;
    private static final long MIN_HISTORY_UPDATE_INTERVAL_MS = 30_000;

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
        AtomicLong lastHistoryUpdateTime = new AtomicLong(0);

        lastNotificationTime = sendTaskProgressNotification(taskId, 0, "Starting book recommendation update", TaskStatus.IN_PROGRESS, lastNotificationTime, lastHistoryUpdateTime, true);

        // Load books in batches, generate embeddings, store lightweight data only
        long totalBooks = bookQueryService.countAllNonDeleted();
        Map<Long, double[]> embeddings = new HashMap<>();
        Map<Long, String> seriesNames = new HashMap<>();

        lastNotificationTime = sendTaskProgressNotification(taskId, 2, String.format("Found %d books, generating embeddings in batches...", totalBooks), TaskStatus.IN_PROGRESS, lastNotificationTime, lastHistoryUpdateTime, false);

        int embeddingProgress = 0;
        int batchPage = 0;
        while (true) {
            List<BookEntity> batch = bookQueryService.getAllFullBookEntitiesBatch(
                    PageRequest.of(batchPage, BATCH_SIZE));
            if (batch.isEmpty()) break;

            for (BookEntity book : batch) {
                if (isCancelled(taskId)) return cancelled(builder, taskId, lastNotificationTime, lastHistoryUpdateTime);
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
                    TaskStatus.IN_PROGRESS, lastNotificationTime, lastHistoryUpdateTime, false);

            if (batch.size() < BATCH_SIZE) break;
            batchPage++;
        }

        lastNotificationTime = sendTaskProgressNotification(taskId, 35, "Computing book similarities...", TaskStatus.IN_PROGRESS, lastNotificationTime, lastHistoryUpdateTime, false);

        // Compute similarities using only in-memory vectors (no entities needed)
        Set<Long> allBookIds = embeddings.keySet();
        Map<Long, Set<BookRecommendationLite>> allRecommendations = new HashMap<>();

        int processedBooks = 0;
        for (Map.Entry<Long, double[]> entry : embeddings.entrySet()) {
            if (isCancelled(taskId)) return cancelled(builder, taskId, lastNotificationTime, lastHistoryUpdateTime);
            Long targetId = entry.getKey();
            try {
                double[] targetVector = entry.getValue();
                if (targetVector == null) continue;

                String targetSeries = seriesNames.get(targetId);

                Comparator<BookVectorService.ScoredBook> worstFirst = Comparator
                        .comparingDouble(BookVectorService.ScoredBook::getScore)
                        .thenComparing(BookVectorService.ScoredBook::getBookId, Comparator.reverseOrder());
                PriorityQueue<BookVectorService.ScoredBook> topCandidates = new PriorityQueue<>(RECOMMENDATION_CANDIDATE_POOL_SIZE, worstFirst);
                int candidateIndex = 0;
                for (Long candidateId : allBookIds) {
                    if ((candidateIndex++ & 255) == 0 && isCancelled(taskId)) {
                        return cancelled(builder, taskId, lastNotificationTime, lastHistoryUpdateTime);
                    }
                    if (candidateId.equals(targetId)) continue;
                    String candidateSeries = seriesNames.get(candidateId);
                    if (targetSeries != null && targetSeries.equals(candidateSeries)) continue;
                    double similarity = vectorService.cosineSimilarity(targetVector, embeddings.get(candidateId));
                    if (similarity <= 0.1) continue;
                    BookVectorService.ScoredBook scored = new BookVectorService.ScoredBook(candidateId, similarity);
                    if (topCandidates.size() < RECOMMENDATION_CANDIDATE_POOL_SIZE) {
                        topCandidates.add(scored);
                    } else if (worstFirst.compare(scored, topCandidates.peek()) > 0) {
                        topCandidates.poll();
                        topCandidates.add(scored);
                    }
                }
                List<BookVectorService.ScoredBook> topSimilar = topCandidates.stream()
                        .sorted(Comparator.comparingDouble(BookVectorService.ScoredBook::getScore).reversed()
                                .thenComparing(BookVectorService.ScoredBook::getBookId))
                        .toList();

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
                        TaskStatus.IN_PROGRESS, lastNotificationTime, lastHistoryUpdateTime, false);
            }
        }

        // Save recommendations in batches
        lastNotificationTime = sendTaskProgressNotification(taskId, 85, String.format("Saving recommendations for %d books...", allRecommendations.size()), TaskStatus.IN_PROGRESS, lastNotificationTime, lastHistoryUpdateTime, false);

        if (isCancelled(taskId)) return cancelled(builder, taskId, lastNotificationTime, lastHistoryUpdateTime);
        bookQueryService.saveRecommendationsInBatches(allRecommendations, BATCH_SIZE, () -> isCancelled(taskId));
        if (isCancelled(taskId)) return cancelled(builder, taskId, lastNotificationTime, lastHistoryUpdateTime);

        long endTime = System.currentTimeMillis();
        log.info("{}: Task completed. Duration: {} ms", getTaskType(), endTime - startTime);

        sendTaskProgressNotification(taskId, 100, String.format("Updated recommendations for %d books in %d ms", totalBooks, endTime - startTime), TaskStatus.COMPLETED, lastNotificationTime, lastHistoryUpdateTime, true);

        return builder
                .status(TaskStatus.COMPLETED)
                .build();
    }

    private boolean isCancelled(String taskId) {
        return cancellationManager.isTaskCancelled(taskId);
    }

    private TaskCreateResponse cancelled(TaskCreateResponse.TaskCreateResponseBuilder builder, String taskId,
                                         long lastNotificationTime, AtomicLong lastHistoryUpdateTime) {
        sendTaskProgressNotification(taskId, 0, "Cancellation requested; stopping recommendation update",
                TaskStatus.CANCELLED, lastNotificationTime, lastHistoryUpdateTime, true);
        return builder.status(TaskStatus.CANCELLED).build();
    }

    private long sendTaskProgressNotification(String taskId, int progress, String message, TaskStatus taskStatus,
                                              long lastNotificationTime, AtomicLong lastHistoryUpdateTime, boolean force) {
        long currentTime = System.currentTimeMillis();

        // Send if forced (start/end) or if enough time has passed
        if (force || (currentTime - lastNotificationTime) >= MIN_NOTIFICATION_INTERVAL_MS) {
            TaskProgressPayload payload = TaskProgressPayload.builder()
                    .taskId(taskId)
                    .taskType(TaskType.UPDATE_BOOK_RECOMMENDATIONS)
                    .message(message)
                    .progress(progress)
                    .taskStatus(taskStatus)
                    .build();
            try {
                notificationService.sendMessage(Topic.TASK_PROGRESS, payload);
            } catch (Exception e) {
                log.error("Failed to send task progress notification for taskId={}: {}", taskId, e.getMessage(), e);
            }

            long previousHistoryUpdate = lastHistoryUpdateTime.get();
            if (force || taskStatus != TaskStatus.IN_PROGRESS
                    || currentTime - previousHistoryUpdate >= MIN_HISTORY_UPDATE_INTERVAL_MS) {
                taskHistoryService.updateTaskProgress(taskId, progress, message);
                lastHistoryUpdateTime.set(currentTime);
            }
            return currentTime;
        }

        return lastNotificationTime;
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.UPDATE_BOOK_RECOMMENDATIONS;
    }
}
