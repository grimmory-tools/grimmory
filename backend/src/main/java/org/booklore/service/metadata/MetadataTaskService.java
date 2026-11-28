package org.booklore.service.metadata;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.FetchedProposalMapper;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.model.dto.MetadataBatchProgressNotification;
import org.booklore.model.dto.MetadataFetchTask;
import org.booklore.model.dto.response.MetadataTaskDetailsResponse;
import org.booklore.model.entity.MetadataFetchJobEntity;
import org.booklore.model.entity.MetadataFetchProposalEntity;
import org.booklore.model.enums.FetchedMetadataProposalStatus;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.booklore.model.enums.TaskType;
import org.booklore.repository.MetadataFetchJobRepository;
import org.booklore.repository.MetadataFetchProposalRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.task.TaskHistoryService;
import org.booklore.task.TaskCancellationManager;
import org.booklore.task.TaskStatus;
import org.booklore.model.websocket.Topic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetadataTaskService {

    private final MetadataFetchJobRepository metadataFetchTaskRepository;
    private final MetadataFetchProposalRepository proposalRepository;
    private final FetchedProposalMapper fetchedProposalMapper;
    private final AuthenticationService authenticationService;
    private final TaskCancellationManager cancellationManager;
    private final TaskHistoryService taskHistoryService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Optional<MetadataTaskDetailsResponse> getTaskWithProposals(String taskId) {
        return metadataFetchTaskRepository.findById(taskId)
                .map(this::buildTaskDetailsResponse);
    }

    private MetadataTaskDetailsResponse buildTaskDetailsResponse(MetadataFetchJobEntity task) {
        List<FetchedProposal> proposals = task.getProposals().stream()
                .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.FETCHED)
                .map(fetchedProposalMapper::toDto)
                .toList();

        MetadataFetchTask taskDto = MetadataFetchTask.builder()
                .id(task.getTaskId())
                .status(task.getStatus())
                .completed(task.getCompletedBooks())
                .totalBooks(task.getTotalBooksCount())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .initiatedBy(task.getUserId())
                .proposals(proposals)
                .build();

        return new MetadataTaskDetailsResponse(taskDto);
    }

    @Transactional
    public boolean deleteTaskAndProposals(String taskId) {
        return metadataFetchTaskRepository.findById(taskId)
                .map(task -> {
                    metadataFetchTaskRepository.delete(task);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean updateProposalStatus(String taskId, Long proposalId, String statusStr) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Optional<FetchedMetadataProposalStatus> statusOpt = parseStatus(statusStr);
        return statusOpt.map(fetchedMetadataProposalStatus -> proposalRepository.findById(proposalId)
                .filter(p -> p.getJob() != null && taskId.equals(p.getJob().getTaskId()))
                .map(proposal -> {
                    proposal.setStatus(fetchedMetadataProposalStatus);
                    proposal.setReviewedAt(Instant.now());
                    proposal.setReviewerUserId(userId);
                    proposalRepository.save(proposal);
                    return true;
                })
                .orElse(false)).orElse(false);

    }

    private Optional<FetchedMetadataProposalStatus> parseStatus(String statusStr) {
        try {
            return Optional.of(FetchedMetadataProposalStatus.valueOf(statusStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<MetadataBatchProgressNotification> getActiveTasks() {
        List<MetadataFetchJobEntity> tasks = metadataFetchTaskRepository.findAllWithProposals();

        return tasks.stream()
                .filter(task -> task.getStatus() == MetadataFetchTaskStatus.COMPLETED
                        || task.getStatus() == MetadataFetchTaskStatus.ERROR
                        || task.getStatus() == MetadataFetchTaskStatus.CANCELLED)
                .map(task -> {
                    List<MetadataFetchProposalEntity> proposals = task.getProposals();
                    List<MetadataFetchProposalEntity> remaining = proposals.stream()
                            .filter(p -> p.getStatus() != FetchedMetadataProposalStatus.REJECTED)
                            .toList();
                    boolean reviewMode = !proposals.isEmpty();

                    int total;

                    String message;
                    String status;
                    int completedCount = task.getCompletedBooks() != null ? task.getCompletedBooks() : 0;

                    if (task.getStatus() == MetadataFetchTaskStatus.ERROR) {
                        total = task.getTotalBooksCount() != null ? task.getTotalBooksCount() : remaining.size();
                        message = task.getStatusMessage() != null && !task.getStatusMessage().isBlank()
                                ? task.getStatusMessage()
                                : String.format("Metadata fetch failed after processing %d of %d books.", completedCount, total);
                        status = "ERROR";
                    } else if (task.getStatus() == MetadataFetchTaskStatus.CANCELLED) {
                        total = task.getTotalBooksCount() != null ? task.getTotalBooksCount() : remaining.size();
                        message = String.format("Metadata fetch cancelled after processing %d of %d books.", completedCount, total);
                        status = "CANCELLED";
                    } else {
                        total = task.getTotalBooksCount() != null ? task.getTotalBooksCount() : remaining.size();
                        message = String.format("Metadata fetch completed successfully. %d books processed.", completedCount);
                        status = "COMPLETED";
                    }

                    return new MetadataBatchProgressNotification(
                            task.getTaskId(),
                            completedCount,
                            total,
                            message,
                            status,
                            reviewMode
                    );
                })
                .filter(n -> n.getTotal() > 0)
                .toList();
    }

    @Transactional
    public List<MetadataBatchProgressNotification> cancelAllInProgressTasks() {
        Instant completedAt = Instant.now();
        List<MetadataFetchJobEntity> jobs = metadataFetchTaskRepository.findAllByStatus(MetadataFetchTaskStatus.IN_PROGRESS);
        List<MetadataBatchProgressNotification> cancelled = new java.util.ArrayList<>();
        for (MetadataFetchJobEntity job : jobs) {
            cancellationManager.cancelTask(job.getTaskId());
            job.setStatus(MetadataFetchTaskStatus.CANCELLED);
            job.setStatusMessage("Metadata batch cancelled by user");
            job.setCompletedAt(completedAt);
            metadataFetchTaskRepository.save(job);
            taskHistoryService.updateTaskStatus(job.getTaskId(), TaskStatus.CANCELLED, "Metadata batch cancelled by user");
            int completed = job.getCompletedBooks() != null ? job.getCompletedBooks() : 0;
            int total = job.getTotalBooksCount() != null ? job.getTotalBooksCount() : completed;
            MetadataBatchProgressNotification progress = new MetadataBatchProgressNotification(job.getTaskId(), completed, total,
                    "Metadata batch cancelled by user", MetadataFetchTaskStatus.CANCELLED.name(), !job.getProposals().isEmpty());
            notificationService.sendMessage(Topic.BOOK_METADATA_BATCH_PROGRESS, progress);
            cancelled.add(progress);
        }

        // Close orphaned task-history rows left behind by a restart even when their job row is missing.
        taskHistoryService.cancelInProgressMetadataTasks();
        return cancelled;
    }
}
