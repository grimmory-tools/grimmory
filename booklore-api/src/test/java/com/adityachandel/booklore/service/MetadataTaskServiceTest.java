package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.mapper.FetchedProposalMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.FetchedProposal;
import com.adityachandel.booklore.model.dto.MetadataBatchProgressNotification;
import com.adityachandel.booklore.model.dto.MetadataFetchTask;
import com.adityachandel.booklore.model.dto.response.MetadataTaskDetailsResponse;
import com.adityachandel.booklore.model.entity.MetadataFetchJobEntity;
import com.adityachandel.booklore.model.entity.MetadataFetchProposalEntity;
import com.adityachandel.booklore.model.enums.FetchedMetadataProposalStatus;
import com.adityachandel.booklore.model.enums.MetadataFetchTaskStatus;
import com.adityachandel.booklore.repository.MetadataFetchJobRepository;
import com.adityachandel.booklore.repository.MetadataFetchProposalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MetadataTaskServiceTest {

    @Mock
    private MetadataFetchJobRepository jobRepository;

    @Mock
    private MetadataFetchProposalRepository proposalRepository;

    @Mock
    private FetchedProposalMapper fetchedProposalMapper;

    @Mock
    private AuthenticationService authenticationService;

    @InjectMocks
    private MetadataTaskService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getTaskWithProposals_shouldReturnEmptyWhenNoTaskFound() {
        when(jobRepository.findById("task1")).thenReturn(Optional.empty());

        Optional<MetadataTaskDetailsResponse> result = service.getTaskWithProposals("task1");

        assertThat(result).isEmpty();
        verify(jobRepository).findById("task1");
        verifyNoMoreInteractions(proposalRepository, fetchedProposalMapper);
    }

    @Test
    void getTaskWithProposals_shouldReturnTaskWithFilteredProposals() {
        MetadataFetchJobEntity jobEntity = mock(MetadataFetchJobEntity.class);
        MetadataFetchProposalEntity p1 = mock(MetadataFetchProposalEntity.class);
        MetadataFetchProposalEntity p2 = mock(MetadataFetchProposalEntity.class);

        when(jobEntity.getTaskId()).thenReturn("task1");
        when(jobEntity.getStatus()).thenReturn(MetadataFetchTaskStatus.IN_PROGRESS);
        when(jobEntity.getCompletedBooks()).thenReturn(2);
        when(jobEntity.getTotalBooksCount()).thenReturn(3);
        Instant now = Instant.now();
        when(jobEntity.getStartedAt()).thenReturn(now.minusSeconds(60));
        when(jobEntity.getCompletedAt()).thenReturn(null);
        when(jobEntity.getUserId()).thenReturn(99L);

        when(p1.getStatus()).thenReturn(FetchedMetadataProposalStatus.FETCHED);
        when(p2.getStatus()).thenReturn(FetchedMetadataProposalStatus.ACCEPTED);

        when(jobEntity.getProposals()).thenReturn(List.of(p1, p2));
        when(jobRepository.findById("task1")).thenReturn(Optional.of(jobEntity));

        FetchedProposal dto1 = mock(FetchedProposal.class);
        when(fetchedProposalMapper.toDto(p1)).thenReturn(dto1);

        Optional<MetadataTaskDetailsResponse> optResponse = service.getTaskWithProposals("task1");
        assertThat(optResponse).isPresent();

        MetadataTaskDetailsResponse response = optResponse.get();
        MetadataFetchTask taskDto = response.getTask();

        assertThat(taskDto.getId()).isEqualTo("task1");
        assertThat(taskDto.getStatus()).isEqualTo(MetadataFetchTaskStatus.IN_PROGRESS);
        assertThat(taskDto.getCompleted()).isEqualTo(2);
        assertThat(taskDto.getTotalBooks()).isEqualTo(3);
        assertThat(taskDto.getStartedAt()).isEqualTo(now.minusSeconds(60));
        assertThat(taskDto.getCompletedAt()).isNull();
        assertThat(taskDto.getInitiatedBy()).isEqualTo(99L);
        assertThat(taskDto.getProposals()).containsExactly(dto1);

        verify(jobRepository).findById("task1");
        verify(fetchedProposalMapper).toDto(p1);
        verifyNoMoreInteractions(proposalRepository);
    }

    @Test
    void deleteTaskAndProposals_shouldDeleteWhenTaskExists() {
        MetadataFetchJobEntity jobEntity = mock(MetadataFetchJobEntity.class);
        when(jobRepository.findById("task1")).thenReturn(Optional.of(jobEntity));

        boolean result = service.deleteTaskAndProposals("task1");

        assertThat(result).isTrue();
        verify(jobRepository).findById("task1");
        verify(jobRepository).delete(jobEntity);
    }

    @Test
    void deleteTaskAndProposals_shouldReturnFalseWhenTaskMissing() {
        when(jobRepository.findById("missing")).thenReturn(Optional.empty());

        boolean result = service.deleteTaskAndProposals("missing");

        assertThat(result).isFalse();
        verify(jobRepository).findById("missing");
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void updateProposalStatus_shouldReturnFalseIfInvalidStatus() {
        BookLoreUser mockedUser = mock(BookLoreUser.class);
        when(mockedUser.getId()).thenReturn(1L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockedUser);
        boolean result = service.updateProposalStatus("task1", 1L, "INVALID_STATUS");
        assertThat(result).isFalse();
        verifyNoInteractions(proposalRepository);
    }


    @Test
    void updateProposalStatus_shouldReturnFalseIfProposalNotFoundOrJobMismatch() {
        BookLoreUser mockedUser = mock(BookLoreUser.class);
        when(mockedUser.getId()).thenReturn(123L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockedUser);

        when(proposalRepository.findById(1L)).thenReturn(Optional.empty());

        boolean result = service.updateProposalStatus("task1", 1L, "ACCEPTED");
        assertThat(result).isFalse();

        MetadataFetchProposalEntity proposal = mock(MetadataFetchProposalEntity.class);
        when(proposal.getJob()).thenReturn(null);
        when(proposalRepository.findById(2L)).thenReturn(Optional.of(proposal));

        boolean result2 = service.updateProposalStatus("task1", 2L, "REJECTED");
        assertThat(result2).isFalse();
    }

    @Test
    void updateProposalStatus_shouldUpdateProposalWhenValid() {
        MetadataFetchProposalEntity proposal = mock(MetadataFetchProposalEntity.class);
        MetadataFetchJobEntity job = mock(MetadataFetchJobEntity.class);
        when(proposal.getJob()).thenReturn(job);
        when(job.getTaskId()).thenReturn("task1");

        BookLoreUser mockedUser = mock(BookLoreUser.class);
        when(mockedUser.getId()).thenReturn(42L);
        when(authenticationService.getAuthenticatedUser()).thenReturn(mockedUser);

        when(proposalRepository.findById(10L)).thenReturn(Optional.of(proposal));

        boolean result = service.updateProposalStatus("task1", 10L, "ACCEPTED");

        assertThat(result).isTrue();
        verify(proposal).setStatus(FetchedMetadataProposalStatus.ACCEPTED);
        verify(proposal).setReviewedAt(any(Instant.class));
        verify(proposal).setReviewerUserId(42L);
        verify(proposalRepository).save(proposal);
    }

    @Test
    void getActiveTasks_shouldReturnOnlyTasksWithRemainingProposals() {
        MetadataFetchJobEntity job1 = mock(MetadataFetchJobEntity.class);
        MetadataFetchProposalEntity p1 = mock(MetadataFetchProposalEntity.class);
        MetadataFetchProposalEntity p2 = mock(MetadataFetchProposalEntity.class);

        when(job1.getTaskId()).thenReturn("task1");
        when(job1.getProposals()).thenReturn(List.of(p1, p2));
        when(p1.getStatus()).thenReturn(FetchedMetadataProposalStatus.ACCEPTED);
        when(p2.getStatus()).thenReturn(FetchedMetadataProposalStatus.REJECTED);

        MetadataFetchJobEntity job2 = mock(MetadataFetchJobEntity.class);
        MetadataFetchProposalEntity p3 = mock(MetadataFetchProposalEntity.class);
        when(job2.getTaskId()).thenReturn("task2");
        when(job2.getProposals()).thenReturn(List.of(p3));
        when(p3.getStatus()).thenReturn(FetchedMetadataProposalStatus.FETCHED);

        when(jobRepository.findAllWithProposals()).thenReturn(List.of(job1, job2));

        List<MetadataBatchProgressNotification> notifications = service.getActiveTasks();

        assertThat(notifications).hasSize(2);

        MetadataBatchProgressNotification n1 = notifications.stream()
                .filter(n -> n.getTaskId().equals("task1"))
                .findFirst().orElseThrow();
        assertThat(n1.getTotal()).isEqualTo(1);
        assertThat(n1.getCompleted()).isEqualTo(1);
        assertThat(n1.getMessage()).contains("Metadata review pending for 0 of 1 books");

        MetadataBatchProgressNotification n2 = notifications.stream()
                .filter(n -> n.getTaskId().equals("task2"))
                .findFirst().orElseThrow();
        assertThat(n2.getTotal()).isEqualTo(1);
        assertThat(n2.getCompleted()).isEqualTo(0);
        assertThat(n2.getMessage()).contains("Metadata review pending for 1 of 1 books");

        verify(jobRepository).findAllWithProposals();
    }

    @Test
    void getActiveTasks_shouldFilterOutTasksWithNoRemainingProposals() {
        MetadataFetchJobEntity job = mock(MetadataFetchJobEntity.class);
        MetadataFetchProposalEntity p1 = mock(MetadataFetchProposalEntity.class);
        when(job.getTaskId()).thenReturn("task1");
        when(job.getProposals()).thenReturn(List.of(p1));
        when(p1.getStatus()).thenReturn(FetchedMetadataProposalStatus.REJECTED);

        when(jobRepository.findAllWithProposals()).thenReturn(List.of(job));

        List<MetadataBatchProgressNotification> notifications = service.getActiveTasks();

        assertThat(notifications).isEmpty();
        verify(jobRepository).findAllWithProposals();
    }
}