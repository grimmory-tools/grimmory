package org.grimmory.service.metadata;

import org.grimmory.config.security.service.AuthenticationService;
import org.grimmory.mapper.FetchedProposalMapper;
import org.grimmory.model.dto.FetchedProposal;
import org.grimmory.model.dto.MetadataBatchProgressNotification;
import org.grimmory.model.dto.MetadataFetchTask;
import org.grimmory.model.dto.response.MetadataTaskDetailsResponse;
import org.grimmory.model.entity.MetadataFetchJobEntity;
import org.grimmory.model.entity.MetadataFetchProposalEntity;
import org.grimmory.model.enums.FetchedMetadataProposalStatus;
import org.grimmory.model.enums.MetadataFetchTaskStatus;
import org.grimmory.repository.MetadataFetchJobRepository;
import org.grimmory.repository.MetadataFetchProposalRepository;
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
                .filter(task -> task.getStatus() == MetadataFetchTaskStatus.COMPLETED || task.getStatus() == MetadataFetchTaskStatus.ERROR)
                .map(task -> {
                    List<MetadataFetchProposalEntity> proposals = task.getProposals();
                    List<MetadataFetchProposalEntity> remaining = proposals.stream()
                            .filter(p -> p.getStatus() != FetchedMetadataProposalStatus.REJECTED)
                            .toList();

                    int total;
                    long acceptedCount = remaining.stream()
                            .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.ACCEPTED)
                            .count();
                    long fetchedCount = remaining.stream()
                            .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.FETCHED)
                            .count();

                    String message;
                    String status;
                    int completedCount = task.getCompletedBooks() != null ? task.getCompletedBooks() : 0;

                    if (task.getStatus() == MetadataFetchTaskStatus.ERROR) {
                        total = task.getTotalBooksCount() != null ? task.getTotalBooksCount() : remaining.size();
                        message = String.format("Metadata fetch failed, processed %d of %d books.", completedCount, total);
                        status = "ERROR";
                    } else {
                        total = remaining.size();
                        message = String.format("Metadata fetch completed! %d books need review.", fetchedCount);
                        status = "COMPLETED";
                        completedCount = (int) acceptedCount;
                    }

                    return new MetadataBatchProgressNotification(
                            task.getTaskId(),
                            completedCount,
                            total,
                            message,
                            status,
                            true
                    );
                })
                .filter(n -> n.getTotal() > 0)
                .toList();
    }
}
