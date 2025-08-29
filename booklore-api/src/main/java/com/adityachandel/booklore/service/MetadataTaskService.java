package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.FetchedProposalMapper;
import com.adityachandel.booklore.model.dto.FetchedProposal;
import com.adityachandel.booklore.model.dto.MetadataBatchProgressNotification;
import com.adityachandel.booklore.model.dto.MetadataFetchTask;
import com.adityachandel.booklore.model.dto.response.MetadataTaskDetailsResponse;
import com.adityachandel.booklore.model.entity.MetadataFetchJobEntity;
import com.adityachandel.booklore.model.entity.MetadataFetchProposalEntity;
import com.adityachandel.booklore.model.enums.FetchedMetadataProposalStatus;
import com.adityachandel.booklore.repository.MetadataFetchJobRepository;
import com.adityachandel.booklore.repository.MetadataFetchProposalRepository;
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

    public Optional<MetadataTaskDetailsResponse> getTaskWithProposals(String taskId) {
        return metadataFetchTaskRepository.findById(taskId)
                .map(task -> {
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
                });
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

    public boolean updateProposalStatus(String taskId, Long proposalId, String statusStr) {
        Long userId = authenticationService.getAuthenticatedUser().getId();
        Optional<FetchedMetadataProposalStatus> statusOpt = parseStatus(statusStr);
        if (statusOpt.isEmpty()) return false;

        return proposalRepository.findById(proposalId)
                .filter(p -> p.getJob() != null && taskId.equals(p.getJob().getTaskId()))
                .map(proposal -> {
                    proposal.setStatus(statusOpt.get());
                    proposal.setReviewedAt(Instant.now());
                    proposal.setReviewerUserId(userId);
                    proposalRepository.save(proposal);
                    return true;
                })
                .orElse(false);
    }

    private Optional<FetchedMetadataProposalStatus> parseStatus(String statusStr) {
        try {
            return Optional.of(FetchedMetadataProposalStatus.valueOf(statusStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<MetadataBatchProgressNotification> getActiveTasks() {
        List<MetadataFetchJobEntity> tasks = metadataFetchTaskRepository.findAllWithProposals(); // Ensure this uses a fetch join

        return tasks.stream()
                .map(task -> {
                    List<MetadataFetchProposalEntity> proposals = task.getProposals();
                    List<MetadataFetchProposalEntity> remaining = proposals.stream()
                            .filter(p -> p.getStatus() != FetchedMetadataProposalStatus.REJECTED)
                            .toList();

                    int total = remaining.size();
                    long acceptedCount = remaining.stream()
                            .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.ACCEPTED)
                            .count();
                    long fetchedCount = remaining.stream()
                            .filter(p -> p.getStatus() == FetchedMetadataProposalStatus.FETCHED)
                            .count();

                    String message = String.format("Metadata review pending for %d of %d books", fetchedCount, total);

                    return new MetadataBatchProgressNotification(
                            task.getTaskId(),
                            (int) acceptedCount,
                            total,
                            message,
                            "COMPLETED"
                    );
                })
                .filter(n -> n.getTotal() > 0)
                .toList();
    }
}