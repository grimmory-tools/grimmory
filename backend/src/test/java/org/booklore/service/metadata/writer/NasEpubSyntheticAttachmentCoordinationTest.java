package org.booklore.service.metadata.writer;

import jakarta.persistence.EntityManager;
import org.booklore.config.AppProperties;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.mapper.BookMetadataMapper;
import org.booklore.mapper.BookdropFileMapper;
import org.booklore.mapper.LibraryMapper;
import org.booklore.mapper.MetadataClearFlagsMapper;
import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.BulkMetadataUpdateFailure;
import org.booklore.model.dto.BulkMetadataUpdateProgress;
import org.booklore.model.dto.BulkMetadataUpdateResult;
import org.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.booklore.model.dto.request.BookdropFinalizeRequest;
import org.booklore.model.dto.request.FileMoveRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.websocket.LogNotification;
import org.booklore.model.websocket.Severity;
import org.booklore.model.websocket.Topic;
import org.booklore.repository.BookAdditionalFileRepository;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.BookdropFileRepository;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.repository.LibraryRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.service.NotificationService;
import org.booklore.service.appsettings.AppSettingService;
import org.booklore.service.audit.AuditService;
import org.booklore.service.book.BookFileAttachmentService;
import org.booklore.service.book.BookFileDetachmentService;
import org.booklore.service.book.BookService;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.bookdrop.BookDropService;
import org.booklore.service.bookdrop.BookdropMonitoringService;
import org.booklore.service.bookdrop.BookdropNotificationService;
import org.booklore.service.epub.EpubRepairService;
import org.booklore.service.file.BookFileMutationGuard;
import org.booklore.service.file.FileMoveHelper;
import org.booklore.service.file.FileMoveService;
import org.booklore.service.file.FileMovingHelper;
import org.booklore.service.metadata.BookCoverService;
import org.booklore.service.metadata.BookMetadataService;
import org.booklore.service.metadata.BookMetadataUpdater;
import org.booklore.service.metadata.CoverImageGenerator;
import org.booklore.service.metadata.MetadataRefreshService;
import org.booklore.service.metadata.extractor.CbxMetadataExtractor;
import org.booklore.service.metadata.extractor.MetadataExtractorFactory;
import org.booklore.service.fileprocessor.BookFileProcessorRegistry;
import org.booklore.service.metadata.sidecar.SidecarMetadataMapper;
import org.booklore.service.metadata.sidecar.SidecarMetadataReader;
import org.booklore.service.metadata.sidecar.SidecarMetadataWriter;
import org.booklore.service.metadata.sidecar.SidecarService;
import org.booklore.service.monitoring.MonitoringRegistrationService;
import org.booklore.service.progress.ReadingProgressService;
import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class NasEpubSyntheticAttachmentCoordinationTest {
    private BookRepository books;
    private BookFileAttachmentService attachment;
    private BookFileDetachmentService detachment;
    private FileMoveService fileMoves;
    private BookMetadataService metadata;
    private SidecarService sidecars;
    private BookCoverService covers;
    private BookQueryService bookQuery;
    private NotificationService notifications;
    private EpubRepairService repairs;
    private BookFileRepository repairFiles;
    private BookDropService bookdrop;
    private BookdropFileRepository bookdropFiles;
    private BookdropMonitoringService bookdropMonitoring;
    private NasEpubSyntheticReplacementCoordinator coordinator;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        transactions = new TransactionTemplate(manager);
        var agent = mock(NasEpubSyntheticReplacementCoordinator.Agent.class);
        when(agent.prepareReplacement(any())).thenAnswer(invocation -> {
            NasEpubJobStore.JobRequest request = invocation.getArgument(0);
            return new NasEpubSyntheticReplacement.Result(request.operationId(), NasEpubSyntheticReplacement.State.PREPARED);
        });
        when(agent.rollbackReplacement(any())).thenAnswer(invocation -> {
            NasEpubJobStore.JobRequest request = invocation.getArgument(0);
            return new NasEpubSyntheticReplacement.Result(request.operationId(), NasEpubSyntheticReplacement.State.ROLLED_BACK);
        });
        coordinator = new NasEpubSyntheticReplacementCoordinator(jdbc, manager, agent);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var guard = new BookFileMutationGuard(List.of(gate::requireAvailable));
        books = mock(BookRepository.class);
        attachment = attachment(guard);
        detachment = new BookFileDetachmentService(books, mock(BookFileRepository.class),
                mock(UserBookProgressRepository.class), mock(AuthenticationService.class),
                mock(ReadingProgressService.class), mock(BookMapper.class), mock(BookService.class),
                mock(AuditService.class), mock(BookCoverService.class), guard);
        fileMoves = new FileMoveService(mock(AppProperties.class), books, mock(BookAdditionalFileRepository.class),
                mock(LibraryRepository.class), mock(FileMoveHelper.class), mock(MonitoringRegistrationService.class),
                mock(LibraryMapper.class), mock(BookMapper.class), mock(NotificationService.class),
                mock(EntityManager.class), transactions, mock(SidecarMetadataWriter.class), guard);
        var clearFlagsMapper = mock(MetadataClearFlagsMapper.class);
        when(clearFlagsMapper.toClearFlags(any())).thenReturn(new MetadataClearFlags());
        metadata = new BookMetadataService(books, mock(BookMapper.class), mock(BookMetadataMapper.class),
                mock(BookMetadataUpdater.class), mock(NotificationService.class), mock(BookMetadataRepository.class),
                mock(BookQueryService.class), mock(AuditService.class), Map.of(), mock(CbxMetadataExtractor.class),
                mock(MetadataExtractorFactory.class), clearFlagsMapper, manager, mock(AppSettingService.class), guard);
        sidecars = new SidecarService(books, mock(LibraryRepository.class), mock(SidecarMetadataReader.class),
                mock(SidecarMetadataWriter.class), mock(SidecarMetadataMapper.class), mock(BookMetadataUpdater.class), guard);
        bookQuery = mock(BookQueryService.class);
        notifications = mock(NotificationService.class);
        covers = new BookCoverService(mock(AppProperties.class), books, notifications, mock(AppSettingService.class),
                mock(FileService.class), mock(BookFileProcessorRegistry.class), bookQuery, mock(CoverImageGenerator.class),
                mock(MetadataWriterFactory.class), mock(SidecarMetadataWriter.class), Runnable::run,
                transactions, mock(AuthenticationService.class), guard);
        repairFiles = mock(BookFileRepository.class);
        repairs = new EpubRepairService(repairFiles, guard, transactions);
        bookdropFiles = mock(BookdropFileRepository.class);
        bookdropMonitoring = mock(BookdropMonitoringService.class);
        bookdrop = new BookDropService(bookdropFiles, mock(LibraryRepository.class), books, bookdropMonitoring,
                mock(NotificationService.class), mock(MetadataRefreshService.class), mock(BookdropNotificationService.class),
                mock(BookFileProcessorRegistry.class), mock(AppProperties.class), mock(BookdropFileMapper.class),
                mock(tools.jackson.databind.ObjectMapper.class), mock(FileMovingHelper.class),
                mock(MonitoringRegistrationService.class), mock(ApplicationEventPublisher.class), guard);
    }

    @Test
    void reservedWorkRejectsBookdropBeforeReadingOrPausingMonitoring() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        var importRequest = new BookdropFinalizeRequest();
        importRequest.setSelectAll(false);
        importRequest.setFiles(List.of());

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> bookdrop.finalizeImport(importRequest)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        verifyNoInteractions(bookdropFiles, books, bookdropMonitoring);

        coordinator.abort(request.operationId());
        transactions.executeWithoutResult(status -> bookdrop.finalizeImport(importRequest));
        verify(bookdropMonitoring).pauseMonitoring();
        verify(bookdropMonitoring).resumeMonitoring();
    }

    @Test
    void attachmentWithoutConfiguredParticipantsPreservesNormalBehavior() {
        var unconfigured = attachment(new BookFileMutationGuard(List.of()));
        assertThatThrownBy(() -> unconfigured.attachBookFiles(1L, List.of(2L), true))
                .isInstanceOf(APIException.class);
        verify(books).findByIdWithBookFiles(1L);
    }

    @Test
    void reservedWorkRejectsAttachmentAndDetachmentBeforeReadingBooks() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> attachment.attachBookFiles(1L, List.of(2L), true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> detachment.detachBookFile(1L, 2L, false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        verifyNoInteractions(books);

        assertThat(coordinator.abort(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> attachment.attachBookFiles(1L, List.of(2L), true))).isInstanceOf(APIException.class);
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> detachment.detachBookFile(1L, 2L, false))).isInstanceOf(APIException.class);
        verify(books, times(2)).findByIdWithBookFiles(1L);
    }

    @Test
    void reservedWorkRejectsFileMovesBeforeReadingBooks() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> fileMoves.bulkMoveFiles(new FileMoveRequest())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> fileMoves.moveSingleFile(null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        verifyNoInteractions(books);

        coordinator.abort(request.operationId());
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> fileMoves.moveSingleFile(null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("only supported on local storage");
    }

    @Test
    void reservedWorkRejectsMetadataAndSidecarsBeforeReadingBooks() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                status -> metadata.updateBookMetadata(1L, null, false, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> sidecars.importFromSidecar(1L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> sidecars.exportToSidecar(1L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> sidecars.bulkImport(1L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> sidecars.bulkExport(1L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");

        var bulk = new BulkMetadataUpdateRequest();
        bulk.setBookIds(Set.of(1L));
        var progress = new ArrayList<BulkMetadataUpdateProgress>();
        assertThat(metadata.bulkUpdateMetadata(bulk, false, false, false, progress::add))
                .isEqualTo(new BulkMetadataUpdateResult(1, 1, 0, 1));
        assertThat(progress.getFirst().failedBooks()).hasSize(1);
        BulkMetadataUpdateFailure failure = progress.getFirst().failedBooks().getFirst();
        assertThat(failure.reason()).contains("reserved");
        verifyNoInteractions(books);

        coordinator.abort(request.operationId());
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> sidecars.importFromSidecar(1L)))
                .isInstanceOf(APIException.class);
        verify(books).findByIdWithBookFiles(1L);
    }

    @Test
    void reservedWorkRejectsSynchronousAndAsynchronousCovers() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> covers.generateCustomCover(1L)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");

        var book = BookEntity.builder().id(1L)
                .metadata(BookMetadataEntity.builder().title("Synthetic").coverLocked(false).build())
                .build();
        when(bookQuery.findAllWithMetadataByIds(Set.of(1L))).thenReturn(List.of(book));
        covers.generateCustomCoversForBooks(Set.of(1L));
        verifyNoInteractions(books);
        verify(notifications, atLeastOnce()).sendMessageToPermissions(eq(Topic.LOG),
                argThat(message -> message instanceof LogNotification notification
                        && notification.getSeverity() == Severity.ERROR
                        && notification.getMessage().contains("reserved")), any());

        coordinator.abort(request.operationId());
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> covers.generateCustomCover(1L)))
                .isInstanceOf(APIException.class);
        verify(books).findByIdWithBookFiles(1L);
    }

    @Test
    void reservedWorkRejectsRepairBeforeLoadingFileAndReleasesAfterAbort() {
        when(repairFiles.countActiveBookFilesByType(BookFileType.EPUB)).thenReturn(1L);
        when(repairFiles.findActiveBookFileIdsByTypeAfter(anyLong(), eq(BookFileType.EPUB), any()))
                .thenReturn(List.of(1L), List.of(), List.of(1L), List.of());
        var request = request();
        coordinator.prepareAndEnqueue(request);

        assertThat(repairs.repairAll("test", () -> false, progress -> {}).failedCount()).isEqualTo(1);
        verify(repairFiles, never()).findAllForRepairByIdIn(any(), any());

        coordinator.abort(request.operationId());
        assertThat(repairs.repairAll("test", () -> false, progress -> {}).failedCount()).isEqualTo(1);
        verify(repairFiles).findAllForRepairByIdIn(List.of(1L), BookFileType.EPUB);
    }

    @Test
    void metadataTransactionBlocksNewReservationUntilItEnds() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var prepareStarted = new CountDownLatch(1);
        when(books.findAllWithMetadataByIds(any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
            return List.of();
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var updating = pool.submit(() -> assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> metadata.updateBookMetadata(1L, null, false, null))).isInstanceOf(APIException.class));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var preparing = pool.submit(() -> {
                    prepareStarted.countDown();
                    return coordinator.prepareAndEnqueue(request());
                });
                assertThat(prepareStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> preparing.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                updating.get(10, TimeUnit.SECONDS);
                assertThat(preparing.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void attachmentTransactionBlocksNewReservationUntilItEnds() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var prepareStarted = new CountDownLatch(1);
        when(books.findByIdWithBookFiles(anyLong())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
            return Optional.empty();
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var attaching = pool.submit(() -> assertThatThrownBy(() -> transactions.executeWithoutResult(
                        status -> attachment.attachBookFiles(1L, List.of(2L), true))).isInstanceOf(APIException.class));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var preparing = pool.submit(() -> {
                    prepareStarted.countDown();
                    return coordinator.prepareAndEnqueue(request());
                });
                assertThat(prepareStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> preparing.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                attaching.get(10, TimeUnit.SECONDS);
                assertThat(preparing.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void bookdropTransactionBlocksNewReservationUntilItEnds() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var prepareStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
            return null;
        }).when(bookdropMonitoring).pauseMonitoring();
        var importRequest = new BookdropFinalizeRequest();
        importRequest.setSelectAll(false);
        importRequest.setFiles(List.of());
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var importing = pool.submit(() -> transactions.executeWithoutResult(
                        status -> bookdrop.finalizeImport(importRequest)));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var preparing = pool.submit(() -> {
                    prepareStarted.countDown();
                    return coordinator.prepareAndEnqueue(request());
                });
                assertThat(prepareStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> preparing.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                importing.get(10, TimeUnit.SECONDS);
                assertThat(preparing.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
            } finally {
                release.countDown();
            }
        }
    }

    private static NasEpubJobStore.JobRequest request() {
        return new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "output.epub", "0".repeat(64), "New", null))));
    }

    private BookFileAttachmentService attachment(BookFileMutationGuard guard) {
        return new BookFileAttachmentService(books, mock(BookFileRepository.class),
                mock(UserBookProgressRepository.class), mock(AuthenticationService.class),
                mock(ReadingProgressService.class), mock(MonitoringRegistrationService.class),
                mock(FileMoveHelper.class), mock(BookMapper.class), mock(BookService.class),
                mock(EntityManager.class), guard);
    }
}
