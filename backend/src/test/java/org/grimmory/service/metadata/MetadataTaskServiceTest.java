package org.booklore.service.metadata;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.FetchedProposalMapper;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.model.dto.MetadataBatchProgressNotification;
import org.booklore.model.dto.response.MetadataTaskDetailsResponse;
import org.booklore.model.entity.MetadataFetchJobEntity;
import org.booklore.model.entity.MetadataFetchProposalEntity;
import org.booklore.model.enums.FetchedMetadataProposalStatus;
import org.booklore.model.enums.MetadataFetchTaskStatus;
import org.booklore.repository.MetadataFetchJobRepository;
import org.booklore.repository.MetadataFetchProposalRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetadataTaskServiceTest {

    @Mock
    private MetadataFetchJobRepository metadataFetchTaskRepository;

    @Mock
    private MetadataFetchProposalRepository proposalRepository;

    @Mock
    private FetchedProposalMapper fetchedProposalMapper;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private MetadataTaskService service;

    private MetadataFetchJobEntity buildTask(String taskId, MetadataFetchTaskStatus status, List<MetadataFetchProposalEntity> proposals) {
        return MetadataFetchJobEntity.builder()
                .taskId(taskId)
                .status(status)
                .userId(1L)
                .startedAt(Instant.now())
                .totalBooksCount(10)
                .completedBooks(5)
                .proposals(proposals)
                .build();
    }

    private MetadataFetchProposalEntity buildProposal(Long id, MetadataFetchJobEntity job, FetchedMetadataProposalStatus status) {
        return MetadataFetchProposalEntity.builder()
                .proposalId(id)
                .job(job)
                .bookId(100L)
                .status(status)
                .build();
    }

    @Nested
    class GetTaskWithProposals {

        @Test
        void returnsEmptyWhenTaskNotFound() {
            when(metadataFetchTaskRepository.findById("missing")).thenReturn(Optional.empty());
            assertThat(service.getTaskWithProposals("missing")).isEmpty();
        }

        @Test
        void returnsResponseWithOnlyFetchedProposals() {
            MetadataFetchJobEntity task = buildTask("t1", MetadataFetchTaskStatus.COMPLETED, new ArrayList<>());
            MetadataFetchProposalEntity fetched = buildProposal(1L, task, FetchedMetadataProposalStatus.FETCHED);
            MetadataFetchProposalEntity accepted = buildProposal(2L, task, FetchedMetadataProposalStatus.ACCEPTED);
            MetadataFetchProposalEntity rejected = buildProposal(3L, task, FetchedMetadataProposalStatus.REJECTED);
            task.setProposals(List.of(fetched, accepted, rejected));

            when(metadataFetchTaskRepository.findById("t1")).thenReturn(Optional.of(task));
            when(fetchedProposalMapper.toDto(fetched)).thenReturn(FetchedProposal.builder().proposalId(1L).build());

            Optional<MetadataTaskDetailsResponse> result = service.getTaskWithProposals("t1");

            assertThat(result).isPresent();
            assertThat(result.get().getTask().getProposals()).hasSize(1);
            assertThat(result.get().getTask().getProposals().getFirst().getProposalId()).isEqualTo(1L);
            verify(fetchedProposalMapper, times(1)).toDto(any());
        }

        @Test
        void mapsTaskFieldsCorrectly() {
            MetadataFetchJobEntity task = buildTask("t2", MetadataFetchTaskStatus.IN_PROGRESS, new ArrayList<>());
            task.setCompletedAt(Instant.now());

            when(metadataFetchTaskRepository.findById("t2")).thenReturn(Optional.of(task));

            var result = service.getTaskWithProposals("t2").orElseThrow();
            var dto = result.getTask();

            assertThat(dto.getId()).isEqualTo("t2");
            assertThat(dto.getStatus()).isEqualTo(MetadataFetchTaskStatus.IN_PROGRESS);
            assertThat(dto.getCompleted()).isEqualTo(5);
            assertThat(dto.getTotalBooks()).isEqualTo(10);
            assertThat(dto.getInitiatedBy()).isEqualTo(1L);
        }
    }

    @Nested
    class DeleteTaskAndProposals {

        @Test
        void returnsTrueAndDeletesWhenFound() {
            MetadataFetchJobEntity task = buildTask("t1", MetadataFetchTaskStatus.COMPLETED, new ArrayList<>());
            when(metadataFetchTaskRepository.findById("t1")).thenReturn(Optional.of(task));

            assertThat(service.deleteTaskAndProposals("t1")).isTrue();
            verify(metadataFetchTaskRepository).delete(task);
        }

        @Test
        void returnsFalseWhenNotFound() {
            when(metadataFetchTaskRepository.findById("missing")).thenReturn(Optional.empty());
            assertThat(service.deleteTaskAndProposals("missing")).isFalse();
            verify(metadataFetchTaskRepository, never()).delete(any());
        }
    }

    @Nested
    class UpdateProposalStatus {

        @Test
        void updatesProposalStatusSuccessfully() {
            Long userId = 42L;
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(userId).build());

            MetadataFetchJobEntity job = buildTask("t1", MetadataFetchTaskStatus.COMPLETED, new ArrayList<>());
            MetadataFetchProposalEntity proposal = buildProposal(10L, job, FetchedMetadataProposalStatus.FETCHED);

            when(proposalRepository.findById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "ACCEPTED");

            assertThat(result).isTrue();
            assertThat(proposal.getStatus()).isEqualTo(FetchedMetadataProposalStatus.ACCEPTED);
            assertThat(proposal.getReviewerUserId()).isEqualTo(userId);
            assertThat(proposal.getReviewedAt()).isNotNull();
            verify(proposalRepository).save(proposal);
        }

        @Test
        void returnsFalseForInvalidStatus() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            boolean result = service.updateProposalStatus("t1", 10L, "INVALID_STATUS");

            assertThat(result).isFalse();
            verify(proposalRepository, never()).save(any());
        }

        @Test
        void returnsFalseWhenProposalNotFound() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());
            when(proposalRepository.findById(99L)).thenReturn(Optional.empty());

            boolean result = service.updateProposalStatus("t1", 99L, "ACCEPTED");

            assertThat(result).isFalse();
        }

        @Test
        void returnsFalseWhenProposalTaskIdMismatch() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            MetadataFetchJobEntity differentJob = buildTask("other-task", MetadataFetchTaskStatus.COMPLETED, new ArrayList<>());
            MetadataFetchProposalEntity proposal = buildProposal(10L, differentJob, FetchedMetadataProposalStatus.FETCHED);
            when(proposalRepository.findById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "ACCEPTED");

            assertThat(result).isFalse();
            verify(proposalRepository, never()).save(any());
        }

        @Test
        void returnsFalseWhenProposalJobIsNull() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            MetadataFetchProposalEntity proposal = MetadataFetchProposalEntity.builder()
                    .proposalId(10L)
                    .job(null)
                    .status(FetchedMetadataProposalStatus.FETCHED)
                    .build();
            when(proposalRepository.findById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "ACCEPTED");

            assertThat(result).isFalse();
        }

        @Test
        void handlesLowercaseStatusString() {
            when(authenticationService.getAuthenticatedUser())
                    .thenReturn(BookLoreUser.builder().id(1L).build());

            MetadataFetchJobEntity job = buildTask("t1", MetadataFetchTaskStatus.COMPLETED, new ArrayList<>());
            MetadataFetchProposalEntity proposal = buildProposal(10L, job, FetchedMetadataProposalStatus.FETCHED);
            when(proposalRepository.findById(10L)).thenReturn(Optional.of(proposal));

            boolean result = service.updateProposalStatus("t1", 10L, "rejected");

            assertThat(result).isTrue();
            assertThat(proposal.getStatus()).isEqualTo(FetchedMetadataProposalStatus.REJECTED);
        }
    }

    @Nested
    class GetActiveTasks {

        @Test
        void filtersOutInProgressAndCancelledTasks() {
            MetadataFetchJobEntity inProgress = buildTask("ip", MetadataFetchTaskStatus.IN_PROGRESS, new ArrayList<>());
            MetadataFetchJobEntity cancelled = buildTask("ca", MetadataFetchTaskStatus.CANCELLED, new ArrayList<>());
            MetadataFetchJobEntity completed = buildTask("co", MetadataFetchTaskStatus.COMPLETED, List.of(
                    MetadataFetchProposalEntity.builder().proposalId(1L).status(FetchedMetadataProposalStatus.FETCHED).build()
            ));

            when(metadataFetchTaskRepository.findAllWithProposals())
                    .thenReturn(List.of(inProgress, cancelled, completed));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTaskId()).isEqualTo("co");
        }

        @Test
        void completedTaskCountsAcceptedAsCompleted() {
            MetadataFetchProposalEntity accepted1 = MetadataFetchProposalEntity.builder()
                    .proposalId(1L).status(FetchedMetadataProposalStatus.ACCEPTED).build();
            MetadataFetchProposalEntity accepted2 = MetadataFetchProposalEntity.builder()
                    .proposalId(2L).status(FetchedMetadataProposalStatus.ACCEPTED).build();
            MetadataFetchProposalEntity fetched = MetadataFetchProposalEntity.builder()
                    .proposalId(3L).status(FetchedMetadataProposalStatus.FETCHED).build();
            MetadataFetchProposalEntity rejected = MetadataFetchProposalEntity.builder()
                    .proposalId(4L).status(FetchedMetadataProposalStatus.REJECTED).build();

            MetadataFetchJobEntity task = buildTask("co", MetadataFetchTaskStatus.COMPLETED,
                    List.of(accepted1, accepted2, fetched, rejected));

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            var notification = result.getFirst();
            assertThat(notification.getCompleted()).isEqualTo(2);
            assertThat(notification.getTotal()).isEqualTo(3);
            assertThat(notification.getStatus()).isEqualTo("COMPLETED");
            assertThat(notification.getMessage()).contains("1 books need review");
        }

        @Test
        void errorTaskUsesTotalBooksCountAndCompletedBooks() {
            MetadataFetchProposalEntity fetched = MetadataFetchProposalEntity.builder()
                    .proposalId(1L).status(FetchedMetadataProposalStatus.FETCHED).build();

            MetadataFetchJobEntity task = MetadataFetchJobEntity.builder()
                    .taskId("err")
                    .status(MetadataFetchTaskStatus.ERROR)
                    .totalBooksCount(20)
                    .completedBooks(15)
                    .startedAt(Instant.now())
                    .proposals(List.of(fetched))
                    .build();

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            var notification = result.getFirst();
            assertThat(notification.getCompleted()).isEqualTo(15);
            assertThat(notification.getTotal()).isEqualTo(20);
            assertThat(notification.getStatus()).isEqualTo("ERROR");
            assertThat(notification.getMessage()).contains("failed");
        }

        @Test
        void errorTaskFallsBackToRemainingSizeWhenTotalBooksCountNull() {
            MetadataFetchProposalEntity fetched1 = MetadataFetchProposalEntity.builder()
                    .proposalId(1L).status(FetchedMetadataProposalStatus.FETCHED).build();
            MetadataFetchProposalEntity fetched2 = MetadataFetchProposalEntity.builder()
                    .proposalId(2L).status(FetchedMetadataProposalStatus.FETCHED).build();

            MetadataFetchJobEntity task = MetadataFetchJobEntity.builder()
                    .taskId("err")
                    .status(MetadataFetchTaskStatus.ERROR)
                    .totalBooksCount(null)
                    .completedBooks(null)
                    .startedAt(Instant.now())
                    .proposals(List.of(fetched1, fetched2))
                    .build();

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getTotal()).isEqualTo(2);
            assertThat(result.getFirst().getCompleted()).isEqualTo(0);
        }

        @Test
        void filtersOutTasksWithZeroTotal() {
            MetadataFetchJobEntity task = buildTask("empty", MetadataFetchTaskStatus.COMPLETED, List.of(
                    MetadataFetchProposalEntity.builder().proposalId(1L).status(FetchedMetadataProposalStatus.REJECTED).build()
            ));

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).isEmpty();
        }

        @Test
        void allNotificationsHaveIsReviewTrue() {
            MetadataFetchProposalEntity fetched = MetadataFetchProposalEntity.builder()
                    .proposalId(1L).status(FetchedMetadataProposalStatus.FETCHED).build();

            MetadataFetchJobEntity task = buildTask("t1", MetadataFetchTaskStatus.COMPLETED, List.of(fetched));

            when(metadataFetchTaskRepository.findAllWithProposals()).thenReturn(List.of(task));

            List<MetadataBatchProgressNotification> result = service.getActiveTasks();

            assertThat(result).allMatch(MetadataBatchProgressNotification::isReview);
        }
    }
}
