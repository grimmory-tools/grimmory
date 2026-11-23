package org.booklore.service.metadata.writer;

import org.booklore.service.file.BookFileMutationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasEpubSyntheticReplacementCoordinatorTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private FakeAgent agent;
    private NasEpubSyntheticReplacementCoordinator coordinator;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE synthetic_metadata (title VARCHAR(255) NOT NULL)");
        manager = new DataSourceTransactionManager(source);
        agent = new FakeAgent();
        coordinator = new NasEpubSyntheticReplacementCoordinator(jdbc, manager, agent);
    }

    @Test
    void lostPreparationResponseLeavesDurableRequestAndCanBeRetried() {
        var request = request();
        agent.losePrepareResponse = true;
        agent.onPrepare = () -> assertThat(coordinator.status(request.operationId()))
                .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PREPARING);
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PREPARING);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
        var changed = new NasEpubJobStore.JobRequest(request.operationId(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "other.epub", "0".repeat(64), "New", null))));
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(changed))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("different request");
        assertThat(coordinator.prepareAndEnqueue(request)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
    }

    @Test
    void abandonedPreparationIsRecoveredAndRollbackCanBeRetried() {
        var request = request();
        agent.losePrepareResponse = true;
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request))
                .isInstanceOf(IllegalStateException.class);
        agent.rollbackUnavailable = true;
        assertThatThrownBy(() -> coordinator.recoverAbandoned(1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORT_PREPARING);
        agent.rollbackUnavailable = false;
        assertThat(coordinator.recoverAbandoned(1)).isEqualTo(1);
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void unavailableAgentLeavesPreparationRecordedForRecovery() {
        var request = request();
        agent.prepareUnavailable = true;
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        agent.rollbackUnavailable = true;
        assertThatThrownBy(() -> coordinator.recoverAbandoned(1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORT_PREPARING);
        agent.rollbackUnavailable = false;
        assertThat(coordinator.recoverAbandoned(1)).isEqualTo(1);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void writeGateRemainsReservedUntilInterruptedPreparationIsRecovered() {
        var request = request();
        agent.prepareUnavailable = true;
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request)).isInstanceOf(IllegalStateException.class);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var transactions = new TransactionTemplate(manager);
        assertThatThrownBy(gate::requireAvailable)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("writable transaction");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> gate.requireAvailable()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        agent.prepareUnavailable = false;
        assertThat(coordinator.recoverAbandoned(1)).isEqualTo(1);
        transactions.executeWithoutResult(status -> gate.requireAvailable());
    }

    @Test
    void nestedIndependentTransactionFailsBeforeWaitingForItsOwnWriteGate() {
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var outer = new TransactionTemplate(manager);
        var inner = new TransactionTemplate(manager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        outer.executeWithoutResult(status -> {
            gate.requireAvailable();
            gate.requireAvailable();
            assertThatThrownBy(() -> inner.executeWithoutResult(innerStatus -> gate.requireAvailable()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nested independent transaction");
            gate.requireAvailable();
        });
        inner.executeWithoutResult(status -> gate.requireAvailable());
    }

    @Test
    void writeGateTransactionTrackingIsReleasedAfterRollback() {
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var transactions = new TransactionTemplate(manager);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            gate.requireAvailable();
            throw new IllegalStateException("rollback");
        })).hasMessage("rollback");
        transactions.executeWithoutResult(status -> gate.requireAvailable());
    }

    @Test
    void coordinatedChangesMayUseGuardOnlyInOwningTransaction() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var guard = new BookFileMutationGuard(List.of(gate::requireAvailable));
        var independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThat(coordinator.process(request.operationId(), () -> {
            guard.beforeMutation();
            jdbc.update("INSERT INTO synthetic_metadata VALUES ('owned')");
            assertThatThrownBy(() -> independent.executeWithoutResult(status -> guard.beforeMutation()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("nested independent transaction");
            guard.beforeMutation();
        })).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);

        assertThat(metadataCount()).isEqualTo(1);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
    }

    @Test
    void differentOperationCannotAssumeReservationOwnership() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var transactions = new TransactionTemplate(manager);
        var called = new AtomicInteger();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                gate.runAsOwner(UUID.randomUUID(), called::incrementAndGet)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
        assertThat(called).hasValue(0);
        assertThat(coordinator.abort(request.operationId()))
                .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
    }

    @Test
    void failedCoordinatedChangesDoNotLeaveOwnerAuthorizationBehind() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var guard = new BookFileMutationGuard(List.of(gate::requireAvailable));
        agent.rollbackUnavailable = true;

        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {
            guard.beforeMutation();
            throw new IllegalStateException("Database update failed");
        })).isInstanceOf(IllegalStateException.class).hasMessage("Database update failed");

        var transactions = new TransactionTemplate(manager);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> guard.beforeMutation()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        agent.rollbackUnavailable = false;
        assertThat(coordinator.abort(request.operationId()))
                .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        transactions.executeWithoutResult(status -> guard.beforeMutation());
    }

    @Test
    void missingOrUnexpectedReservationFailsBeforeAgentMutation() {
        jdbc.update("DELETE FROM nas_synthetic_write_gate WHERE gate_id = 1");
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("gate is missing");
        assertThat(agent.state).isNull();

        jdbc.update("INSERT INTO nas_synthetic_write_gate (gate_id, operation_id) VALUES (1, NULL)");
        var request = request();
        coordinator.prepareAndEnqueue(request);
        jdbc.update("UPDATE nas_synthetic_write_gate SET operation_id = ? WHERE gate_id = 1",
                UUID.randomUUID().toString());
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {}))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
    }

    @Test
    void writeGateRemainsReservedUntilCommittedReplacementIsFinalized() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        agent.loseFinishResponse = true;
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {}))
                .isInstanceOf(IllegalStateException.class);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var transactions = new TransactionTemplate(manager);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> gate.requireAvailable()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThat(coordinator.recoverCommitted(1)).isEqualTo(1);
        transactions.executeWithoutResult(status -> gate.requireAvailable());
    }

    @Test
    void newWorkFinalizesCommittedDecisionAfterLostResponse() {
        var first = request();
        coordinator.prepareAndEnqueue(first);
        agent.loseFinishResponse = true;
        assertThatThrownBy(() -> coordinator.process(first.operationId(),
                () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);

        var next = request();
        assertThat(coordinator.prepareAndEnqueue(next)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(coordinator.recoverCommitted(1)).isZero();
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(coordinator.abort(next.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        new TransactionTemplate(manager).executeWithoutResult(status -> new NasEpubSyntheticWriteGate(jdbc).requireAvailable());
    }

    @Test
    void newWorkRetriesOnlyAnAlreadyDecidedRollbackAndFailsClosedWhileAgentUnavailable() {
        var first = request();
        coordinator.prepareAndEnqueue(first);
        agent.rollbackUnavailable = true;
        assertThatThrownBy(() -> coordinator.abort(first.operationId())).isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTING);

        var next = request();
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(next))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThatThrownBy(() -> coordinator.status(next.operationId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTING);

        agent.rollbackUnavailable = false;
        assertThat(coordinator.prepareAndEnqueue(next)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
    }

    @Test
    void automaticRecoveryDoesNotAbortPendingWork() {
        var first = request();
        coordinator.prepareAndEnqueue(first);
        assertThat(coordinator.recoverDecided(10)).isZero();
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
        assertThat(agent.rollbackCalls).isZero();
    }

    @Test
    void automaticRecoveryDoesNotAbortPreparingWork() {
        var first = request();
        agent.prepareUnavailable = true;
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(first)).isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.recoverDecided(10)).isZero();
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PREPARING);
        assertThat(agent.rollbackCalls).isZero();
    }

    @Test
    void concurrentFinalizationAndNewWorkAreIdempotent() throws Exception {
        var first = request();
        coordinator.prepareAndEnqueue(first);
        agent.finishEntered = new CountDownLatch(2);
        agent.firstFinishEntered = new CountDownLatch(1);
        agent.releaseFinish = new CountDownLatch(1);
        agent.finishProcessed = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var completing = pool.submit(() -> coordinator.process(first.operationId(),
                        () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')")));
                assertThat(agent.firstFinishEntered.await(5, TimeUnit.SECONDS)).isTrue();
                var next = request();
                var preparing = pool.submit(() -> coordinator.prepareAndEnqueue(next));
                assertThat(agent.finishEntered.await(5, TimeUnit.SECONDS)).isTrue();
                agent.releaseFinish.countDown();
                assertThat(completing.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
                assertThat(preparing.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
                assertThat(metadataCount()).isEqualTo(1);
                assertThat(agent.rollbackCalls).isZero();
            } finally {
                agent.releaseFinish.countDown();
            }
        }
    }

    @Test
    void activeWriteTransactionBlocksPreparationUntilCommit() throws Exception {
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var transactions = new TransactionTemplate(manager);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var prepareStarted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var writer = pool.submit(() -> transactions.executeWithoutResult(status -> {
                    gate.requireAvailable();
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting for test release");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }));
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var preparation = pool.submit(() -> {
                    prepareStarted.countDown();
                    return coordinator.prepareAndEnqueue(request());
                });
                assertThat(prepareStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> preparation.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                writer.get(10, TimeUnit.SECONDS);
                assertThat(preparation.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void lostAgentResponseLeavesDecisionPendingAndAbortCanBeRetried() {
        var request = request();
        assertThat(coordinator.prepareAndEnqueue(request)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
        agent.loseApplyResponse = true;
        agent.rollbackUnavailable = true;
        assertThatThrownBy(() -> coordinator.process(request.operationId(),
                () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('changed')")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback")
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTING);
        assertThat(metadataCount()).isZero();
        assertThatThrownBy(() -> coordinator.abort(request.operationId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTING);
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(
                status -> new NasEpubSyntheticWriteGate(jdbc).requireAvailable()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        agent.rollbackUnavailable = false;
        assertThat(coordinator.recoverAbandoned(100)).isEqualTo(1);
        assertThat(coordinator.abort(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(coordinator.abort(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void recoveryRejectsInvalidLimitsAndAbortsAnAbandonedPendingJob() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> coordinator.recoverAbandoned(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coordinator.recoverAbandoned(101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(coordinator.recoverAbandoned(1)).isEqualTo(1);
        assertThat(coordinator.recoverAbandoned(1)).isZero();
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
    }

    @Test
    void databaseFailureRollsBackMetadataAndAllowsExplicitFileRollback() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {
            jdbc.update("INSERT INTO synthetic_metadata VALUES ('changed')");
            throw new IllegalStateException("Simulated database error");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("database error");
        assertThat(metadataCount()).isZero();
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(coordinator.abort(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void lostCommitAcknowledgementDoesNotUndoCommittedFiles() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {
            jdbc.update("INSERT INTO synthetic_metadata VALUES ('changed')");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    throw new IllegalStateException("Simulated lost commit acknowledgement");
                }
            });
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("acknowledgement");
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(coordinator.abort(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
        assertThat(agent.rollbackCalls).isZero();
        assertThat(coordinator.recoverCommitted(1)).isEqualTo(1);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
        assertThat(coordinator.recoverCommitted(1)).isZero();
        assertThat(metadataCount()).isEqualTo(1);
    }

    @Test
    void lostFinalizationResponseCanBeRetriedWithoutRepeatingDatabaseChanges() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        agent.loseFinishResponse = true;
        assertThatThrownBy(() -> coordinator.process(request.operationId(),
                () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('changed')")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
        assertThat(coordinator.recoverCommitted(1)).isEqualTo(1);
        assertThat(coordinator.recoverCommitted(1)).isZero();
        assertThat(coordinator.process(request.operationId(),
                () -> { throw new AssertionError("Changes must not run twice"); }))
                .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(agent.rollbackCalls).isZero();
    }

    @Test
    void committedRecoveryRejectsInvalidLimitsAndDoesNotTouchPendingWork() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> coordinator.recoverCommitted(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coordinator.recoverCommitted(101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(coordinator.recoverCommitted(1)).isZero();
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
    }

    @Test
    void failedCommittedRecoveryRemainsRetryable() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        agent.loseFinishResponse = true;
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {}))
                .isInstanceOf(IllegalStateException.class);
        agent.loseFinishResponse = true;
        assertThatThrownBy(() -> coordinator.recoverCommitted(1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(coordinator.recoverCommitted(1)).isEqualTo(1);
        assertThat(coordinator.recoverCommitted(1)).isZero();
        assertThat(agent.rollbackCalls).isZero();
    }

    @Test
    void rollbackOnlyTransactionNeverReportsACommittedReplacement() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {
            jdbc.update("INSERT INTO synthetic_metadata VALUES ('changed')");
            var holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(jdbc.getDataSource());
            holder.setRollbackOnly();
        })).isInstanceOf(RuntimeException.class);
        assertThat(metadataCount()).isZero();
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void committedWorkIsIdempotentAndDoesNotRunDatabaseChangesTwice() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThat(coordinator.prepareAndEnqueue(request)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
        assertThat(coordinator.process(request.operationId(),
                () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('changed')")))
                .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(coordinator.process(request.operationId(),
                () -> { throw new AssertionError("Changes must not run twice"); }))
                .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(metadataCount()).isEqualTo(1);
        var changed = new NasEpubJobStore.JobRequest(request.operationId(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "other.epub", "0".repeat(64), "New", null))));
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(changed))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("different request");
    }

    @Test
    void wrongTransactionManagerIsRejectedBeforeContactingAgent() {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        var wrong = new NasEpubSyntheticReplacementCoordinator(jdbc, new DataSourceTransactionManager(source), agent);
        assertThatThrownBy(() -> wrong.prepareAndEnqueue(request()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("decision database");
        assertThat(agent.state).isNull();
    }

    @Test
    void abortWaitsForConcurrentCommitDecision() throws Exception {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        agent.applyEntered = new CountDownLatch(1);
        agent.releaseApply = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var process = pool.submit(() -> coordinator.process(request.operationId(),
                        () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('changed')")));
                assertThat(agent.applyEntered.await(5, TimeUnit.SECONDS)).isTrue();
                var abort = pool.submit(() -> coordinator.abort(request.operationId()));
                agent.releaseApply.countDown();
                assertThat(process.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
                assertThat(abort.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
                assertThat(agent.rollbackCalls).isZero();
            } finally {
                agent.releaseApply.countDown();
            }
        }
    }

    @Test
    void maintenanceStopsWhenRollbackCannotBeConfirmed() {
        var runner = new NasEpubSyntheticMaintenanceRunner(coordinator);
        var results = new ArrayList<NasEpubSyntheticMaintenanceRunner.Result>();
        var subsequentChanges = new AtomicInteger();
        agent.rollbackUnavailable = true;
        var file = request().batch().requests().getFirst();
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> runner.run(runId, List.of(
                new NasEpubSyntheticMaintenanceRunner.Work(1, file, () -> {
                    jdbc.update("INSERT INTO synthetic_metadata VALUES ('failed')");
                    throw new IllegalStateException("Database failure");
                }),
                new NasEpubSyntheticMaintenanceRunner.Work(2, file, subsequentChanges::incrementAndGet)),
                results::add)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database failure")
                .satisfies(e -> assertThat(e.getSuppressed()).isNotEmpty());

        assertThat(results).isEmpty();
        assertThat(subsequentChanges).hasValue(0);
        assertThat(metadataCount()).isZero();
        assertThat(jdbc.queryForList("SELECT status FROM nas_synthetic_replacement_decision", String.class))
                .containsExactly("ABORTING");
        assertThat(runner.reports(runId, 0, 10).getFirst().status())
                .isEqualTo(NasEpubSyntheticMaintenanceRunner.ReportStatus.STARTED);
        assertThatThrownBy(() -> runner.recoverInterrupted(runId, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(runner.reports(runId, 0, 10).getFirst().status())
                .isEqualTo(NasEpubSyntheticMaintenanceRunner.ReportStatus.STARTED);
        agent.rollbackUnavailable = false;
        assertThat(runner.recoverInterrupted(runId, 1)).isEqualTo(1);
        assertThat(runner.reports(runId, 0, 10).getFirst().status())
                .isEqualTo(NasEpubSyntheticMaintenanceRunner.ReportStatus.FAILED);
    }

    @Test
    void maintenanceRecoversCommittedDecisionWithoutReplayingDatabaseChanges() {
        var runner = new NasEpubSyntheticMaintenanceRunner(coordinator);
        var results = new ArrayList<NasEpubSyntheticMaintenanceRunner.Result>();
        var calls = new AtomicInteger();
        runner.run(UUID.randomUUID(), List.of(new NasEpubSyntheticMaintenanceRunner.Work(1,
                request().batch().requests().getFirst(), () -> {
                    calls.incrementAndGet();
                    jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')");
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            throw new IllegalStateException("Lost commit acknowledgement");
                        }
                    });
                })), results::add);

        assertThat(calls).hasValue(1);
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().succeeded()).isTrue();
        assertThat(coordinator.status(results.getFirst().operationId()))
                .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
    }

    @Test
    void maintenanceRecoversLostFinalizationResponseWithoutReplayingDatabaseChanges() {
        var runner = new NasEpubSyntheticMaintenanceRunner(coordinator);
        var results = new ArrayList<NasEpubSyntheticMaintenanceRunner.Result>();
        var calls = new AtomicInteger();
        agent.loseFinishResponse = true;
        runner.run(UUID.randomUUID(), List.of(new NasEpubSyntheticMaintenanceRunner.Work(1,
                request().batch().requests().getFirst(), () -> {
                    calls.incrementAndGet();
                    jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')");
                })), results::add);

        assertThat(calls).hasValue(1);
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().succeeded()).isTrue();
        assertThat(coordinator.recoverCommitted(1)).isZero();
    }

    @Test
    void maintenanceStopsOnInterruptWithoutStartingAnotherBook() {
        var runner = new NasEpubSyntheticMaintenanceRunner(coordinator);
        var results = new ArrayList<NasEpubSyntheticMaintenanceRunner.Result>();
        var subsequentChanges = new AtomicInteger();
        agent.onPrepare = () -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted");
        };
        var file = request().batch().requests().getFirst();
        UUID runId = UUID.randomUUID();
        try {
            assertThatThrownBy(() -> runner.run(runId, List.of(
                    new NasEpubSyntheticMaintenanceRunner.Work(1, file, () -> {}),
                    new NasEpubSyntheticMaintenanceRunner.Work(2, file, subsequentChanges::incrementAndGet)),
                    results::add)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(results).isEmpty();
            assertThat(subsequentChanges).hasValue(0);
            assertThat(agent.rollbackCalls).isZero();
        } finally {
            Thread.interrupted();
        }
        agent.onPrepare = null;
        assertThat(runner.recoverInterrupted(runId, 1)).isEqualTo(1);
        assertThat(runner.reports(runId, 0, 10).getFirst().error()).contains("restored");
    }

    @Test
    void maintenanceRecoversReportAfterCommitAndBeforePreparation() {
        var runner = new NasEpubSyntheticMaintenanceRunner(coordinator);
        UUID runId = UUID.randomUUID();
        var committed = request();
        jdbc.update("INSERT INTO nas_synthetic_maintenance_report "
                        + "(run_id, book_id, operation_id, status) VALUES (?, 1, ?, 'STARTED')",
                runId.toString(), committed.operationId().toString());
        coordinator.prepareAndEnqueue(committed);
        assertThatThrownBy(() -> coordinator.process(committed.operationId(), () -> {
            jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    throw new IllegalStateException("Lost commit acknowledgement");
                }
            });
        })).isInstanceOf(IllegalStateException.class);
        jdbc.update("INSERT INTO nas_synthetic_maintenance_report "
                        + "(run_id, book_id, operation_id, status) VALUES (?, 2, ?, 'STARTED')",
                runId.toString(), UUID.randomUUID().toString());

        assertThat(runner.recoverInterrupted(runId, 10)).isEqualTo(2);
        assertThat(runner.recoverInterrupted(runId, 10)).isZero();
        var reports = runner.reports(runId, 0, 10);
        assertThat(reports).extracting(NasEpubSyntheticMaintenanceRunner.Report::status)
                .containsExactly(NasEpubSyntheticMaintenanceRunner.ReportStatus.SUCCEEDED,
                        NasEpubSyntheticMaintenanceRunner.ReportStatus.FAILED);
        assertThat(reports.get(1).error()).contains("before preparation");
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
    }

    @Test
    void maintenanceRejectsAnExistingTransactionBeforeRecordingWork() {
        var runner = new NasEpubSyntheticMaintenanceRunner(coordinator);
        var transactions = new TransactionTemplate(manager);
        var file = request().batch().requests().getFirst();
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> runner.run(runId,
                List.of(new NasEpubSyntheticMaintenanceRunner.Work(1, file, () -> {})), result -> {})))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside a transaction");
        assertThat(runner.reports(runId, 0, 10)).isEmpty();
    }

    @Test
    void maintenanceKeepsCommittedReportWhenProgressConsumerFails() {
        var runner = new NasEpubSyntheticMaintenanceRunner(coordinator);
        UUID runId = UUID.randomUUID();
        var file = request().batch().requests().getFirst();
        var later = new AtomicInteger();

        assertThatThrownBy(() -> runner.run(runId, List.of(
                new NasEpubSyntheticMaintenanceRunner.Work(1, file,
                        () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')")),
                new NasEpubSyntheticMaintenanceRunner.Work(2, file, later::incrementAndGet)),
                result -> { throw new IllegalStateException("Progress delivery failed"); }))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Progress delivery failed");

        var restarted = new NasEpubSyntheticMaintenanceRunner(coordinator);
        assertThat(restarted.recoverInterrupted(runId, 10)).isZero();
        assertThat(restarted.reports(runId, 0, 10)).singleElement()
                .satisfies(report -> assertThat(report.status())
                        .isEqualTo(NasEpubSyntheticMaintenanceRunner.ReportStatus.SUCCEEDED));
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(later).hasValue(0);
    }

    @Test
    void maintenanceDoesNotPrepareWhenStartingReportCannotBeSaved() {
        var failingJdbc = failReportUpdate("INSERT INTO nas_synthetic_maintenance_report");
        var runner = new NasEpubSyntheticMaintenanceRunner(
                new NasEpubSyntheticReplacementCoordinator(failingJdbc, manager, agent));
        UUID runId = UUID.randomUUID();
        var file = request().batch().requests().getFirst();
        var later = new AtomicInteger();

        assertThatThrownBy(() -> runner.run(runId, List.of(
                new NasEpubSyntheticMaintenanceRunner.Work(1, file, () -> {}),
                new NasEpubSyntheticMaintenanceRunner.Work(2, file, later::incrementAndGet)),
                result -> {})).isInstanceOf(IllegalStateException.class).hasMessage("Report storage unavailable");

        assertThat(agent.state).isNull();
        assertThat(later).hasValue(0);
        assertThat(new NasEpubSyntheticMaintenanceRunner(coordinator).reports(runId, 0, 10)).isEmpty();
    }

    @Test
    void maintenanceStopsAndRecoversCommittedWorkWhenFinishingReportFails() {
        var failingJdbc = failReportUpdate("UPDATE nas_synthetic_maintenance_report");
        var runner = new NasEpubSyntheticMaintenanceRunner(
                new NasEpubSyntheticReplacementCoordinator(failingJdbc, manager, agent));
        UUID runId = UUID.randomUUID();
        var file = request().batch().requests().getFirst();
        var results = new ArrayList<NasEpubSyntheticMaintenanceRunner.Result>();
        var later = new AtomicInteger();

        assertThatThrownBy(() -> runner.run(runId, List.of(
                new NasEpubSyntheticMaintenanceRunner.Work(1, file,
                        () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')")),
                new NasEpubSyntheticMaintenanceRunner.Work(2, file, later::incrementAndGet)),
                results::add)).isInstanceOf(IllegalStateException.class).hasMessage("Report storage unavailable");

        var restarted = new NasEpubSyntheticMaintenanceRunner(coordinator);
        assertThat(results).isEmpty();
        assertThat(later).hasValue(0);
        assertThat(metadataCount()).isEqualTo(1);
        assertThat(restarted.reports(runId, 0, 10)).singleElement()
                .satisfies(report -> assertThat(report.status())
                        .isEqualTo(NasEpubSyntheticMaintenanceRunner.ReportStatus.STARTED));
        assertThat(restarted.recoverInterrupted(runId, 10)).isEqualTo(1);
        assertThat(restarted.reports(runId, 0, 10)).singleElement()
                .satisfies(report -> assertThat(report.status())
                        .isEqualTo(NasEpubSyntheticMaintenanceRunner.ReportStatus.SUCCEEDED));
        assertThat(metadataCount()).isEqualTo(1);
    }

    private JdbcTemplate failReportUpdate(String sqlPrefix) {
        return new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public int update(String sql, Object... args) {
                if (sql.startsWith(sqlPrefix)) {
                    throw new IllegalStateException("Report storage unavailable");
                }
                return super.update(sql, args);
            }
        };
    }

    @Test
    void maintenanceReportsSurviveDatabaseReopen(@TempDir Path directory) {
        String url = "jdbc:h2:file:" + directory.resolve("maintenance").toAbsolutePath()
                + ";MODE=MariaDB;DB_CLOSE_ON_EXIT=FALSE";
        var source = new DriverManagerDataSource(url, "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        var persistentJdbc = new JdbcTemplate(source);
        var persistentCoordinator = new NasEpubSyntheticReplacementCoordinator(
                persistentJdbc, new DataSourceTransactionManager(source), new FakeAgent());
        var runner = new NasEpubSyntheticMaintenanceRunner(persistentCoordinator);
        UUID runId = UUID.randomUUID();
        var file = request().batch().requests().getFirst();
        runner.run(runId, List.of(
                new NasEpubSyntheticMaintenanceRunner.Work(1, file, () -> {}),
                new NasEpubSyntheticMaintenanceRunner.Work(2, file,
                        () -> { throw new IllegalStateException("Book failed"); })), result -> {});

        var reopenedSource = new DriverManagerDataSource(url, "sa", "");
        reopenedSource.setDriverClassName("org.h2.Driver");
        var reopenedCoordinator = new NasEpubSyntheticReplacementCoordinator(new JdbcTemplate(reopenedSource),
                new DataSourceTransactionManager(reopenedSource), new FakeAgent());
        var reports = new NasEpubSyntheticMaintenanceRunner(reopenedCoordinator).reports(runId, 0, 10);
        assertThat(reports).extracting(NasEpubSyntheticMaintenanceRunner.Report::status)
                .containsExactly(NasEpubSyntheticMaintenanceRunner.ReportStatus.SUCCEEDED,
                        NasEpubSyntheticMaintenanceRunner.ReportStatus.FAILED);
        assertThat(reports.get(1).error()).contains("Book failed");
    }

    private int metadataCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class);
    }

    private NasEpubJobStore.JobRequest request() {
        return new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "output.epub", "0".repeat(64), "New", null))));
    }

    private static class FakeAgent implements NasEpubSyntheticReplacementCoordinator.Agent {
        volatile NasEpubSyntheticReplacement.State state;
        boolean loseApplyResponse;
        boolean losePrepareResponse;
        boolean prepareUnavailable;
        boolean rollbackUnavailable;
        boolean loseFinishResponse;
        int rollbackCalls;
        CountDownLatch applyEntered;
        CountDownLatch releaseApply;
        CountDownLatch finishEntered;
        CountDownLatch firstFinishEntered;
        CountDownLatch releaseFinish;
        CountDownLatch finishProcessed;
        Runnable onPrepare;

        @Override
        public NasEpubSyntheticReplacement.Result prepareReplacement(NasEpubJobStore.JobRequest request) throws IOException {
            if (onPrepare != null) {
                onPrepare.run();
            }
            if (prepareUnavailable) {
                throw new IOException("Simulated agent outage");
            }
            state = NasEpubSyntheticReplacement.State.PREPARED;
            if (losePrepareResponse) {
                losePrepareResponse = false;
                throw new IOException("Simulated lost preparation response");
            }
            return new NasEpubSyntheticReplacement.Result(request.operationId(), state);
        }

        @Override
        public NasEpubSyntheticReplacement.Result applyReplacement(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException {
            state = NasEpubSyntheticReplacement.State.APPLIED;
            if (applyEntered != null) {
                applyEntered.countDown();
                if (!releaseApply.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for test release");
                }
            }
            if (loseApplyResponse) {
                loseApplyResponse = false;
                throw new IOException("Simulated lost response");
            }
            return new NasEpubSyntheticReplacement.Result(request.operationId(), state);
        }

        @Override
        public NasEpubSyntheticReplacement.Result rollbackReplacement(NasEpubJobStore.JobRequest request) throws IOException {
            rollbackCalls++;
            if (rollbackUnavailable) {
                throw new IOException("Simulated agent outage");
            }
            state = NasEpubSyntheticReplacement.State.ROLLED_BACK;
            return new NasEpubSyntheticReplacement.Result(request.operationId(), state);
        }

        @Override
        public NasEpubSyntheticReplacement.Result cancelPreparation(NasEpubJobStore.JobRequest request) throws IOException {
            return rollbackReplacement(request);
        }

        @Override
        public NasEpubSyntheticReplacement.Result finishReplacement(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException {
            if (finishEntered != null) {
                finishEntered.countDown();
                firstFinishEntered.countDown();
                if (!releaseFinish.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for finalization");
                }
            }
            if (state != NasEpubSyntheticReplacement.State.APPLIED
                    && state != NasEpubSyntheticReplacement.State.COMMITTED) {
                throw new IOException("Synthetic replacement was not applied");
            }
            state = NasEpubSyntheticReplacement.State.COMMITTED;
            if (finishProcessed != null) {
                finishProcessed.countDown();
                if (!finishProcessed.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for both finalizations");
                }
            }
            if (loseFinishResponse) {
                loseFinishResponse = false;
                throw new IOException("Simulated lost finalization response");
            }
            return new NasEpubSyntheticReplacement.Result(request.operationId(), NasEpubSyntheticReplacement.State.COMMITTED);
        }
    }
}
