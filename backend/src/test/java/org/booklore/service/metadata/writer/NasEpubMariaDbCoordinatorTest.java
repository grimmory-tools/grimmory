package org.booklore.service.metadata.writer;

import org.booklore.service.file.BookFileMutationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "NAS_MARIADB_TEST", matches = "true")
class NasEpubMariaDbCoordinatorTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private TestAgent agent;
    private NasEpubSyntheticReplacementCoordinator coordinator;

    @BeforeEach
    void setUp() {
        var source = isolatedDataSource();
        jdbc = new JdbcTemplate(source);
        agent = new TestAgent();
        manager = new DataSourceTransactionManager(source);
        coordinator = new NasEpubSyntheticReplacementCoordinator(jdbc, manager, agent);
    }

    static DriverManagerDataSource isolatedDataSource() {
        String password = System.getenv("NAS_MARIADB_TEST_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("NAS_MARIADB_TEST_PASSWORD is required");
        }
        String host = System.getenv().getOrDefault("NAS_MARIADB_TEST_HOST", "127.0.0.1");
        if (!host.equals("127.0.0.1") && !host.equals("mariadb-coordinator-test")) {
            throw new IllegalArgumentException("Invalid isolated MariaDB test host");
        }
        int port = host.equals("127.0.0.1") ? 33316 : 3306;
        var source = new DriverManagerDataSource(
                "jdbc:mariadb://" + host + ":" + port + "/grimmory_nas_test?connectTimeout=5000&socketTimeout=10000",
                "grimmory_test", password);
        source.setDriverClassName("org.mariadb.jdbc.Driver");
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).isEqualTo("grimmory_nas_test");
        assertThat(jdbc.queryForList("SELECT marker FROM nas_test_marker", String.class))
                .containsExactly("grimmory-synthetic-nas-coordinator-test-v1");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        jdbc.execute("CREATE TABLE IF NOT EXISTS synthetic_metadata (operation_id CHAR(36) NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('nas_synthetic_replacement_decision', 'nas_synthetic_write_gate', 'synthetic_metadata') AND engine = 'InnoDB'",
                Integer.class)).isEqualTo(3);
        return source;
    }

    @Test
    void databaseFailureRollsBackBeforeRestoringFiles() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {
            insertMetadata(request.operationId());
            throw new IllegalStateException("Simulated database failure");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("database failure");
        assertThat(metadataCount(request.operationId())).isZero();
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void lostPreparationResponseLeavesRecoverableDecision() {
        var request = request();
        agent.losePrepareResponse = true;
        assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PREPARING);
        assertThat(coordinator.recoverAbandoned(100)).isEqualTo(1);
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
    }

    @Test
    void reservationBlocksCooperatingWriterUntilRollbackFinishes() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        var transactions = new TransactionTemplate(manager);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> gate.requireAvailable()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reserved");
        assertThat(coordinator.abort(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        transactions.executeWithoutResult(status -> gate.requireAvailable());
    }

    @Test
    void coordinatedChangesCanUseGuardInOwningTransaction() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var guard = new BookFileMutationGuard(List.of(gate::requireAvailable));
        var independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThat(coordinator.process(request.operationId(), () -> {
            guard.beforeMutation();
            insertMetadata(request.operationId());
            assertThatThrownBy(() -> independent.executeWithoutResult(status -> guard.beforeMutation()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("nested independent transaction");
            guard.beforeMutation();
        })).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(metadataCount(request.operationId())).isEqualTo(1);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
    }

    @Test
    void nestedIndependentWriterIsRejectedWithoutWaitingForParent() {
        var gate = new NasEpubSyntheticWriteGate(jdbc);
        var outer = new TransactionTemplate(manager);
        var inner = new TransactionTemplate(manager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            gate.requireAvailable();
            assertThatThrownBy(() -> inner.executeWithoutResult(innerStatus -> gate.requireAvailable()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nested independent transaction");
            gate.requireAvailable();
        });
        inner.executeWithoutResult(status -> gate.requireAvailable());
    }

    @Test
    void reservationWaitsForCooperatingWriterTransaction() throws Exception {
        var transactions = new TransactionTemplate(manager);
        var gate = new NasEpubSyntheticWriteGate(jdbc);
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
        assertThat(coordinator.recoverAbandoned(100)).isEqualTo(1);
    }

    @Test
    void abortWaitsForPreparationDecision() throws Exception {
        var request = request();
        agent.prepareEntered = new CountDownLatch(1);
        agent.releasePrepare = new CountDownLatch(1);
        var abortStarted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var preparation = pool.submit(() -> coordinator.prepareAndEnqueue(request));
                assertThat(agent.prepareEntered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(coordinator.status(request.operationId()))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PREPARING);
                var abort = pool.submit(() -> {
                    abortStarted.countDown();
                    return coordinator.abort(request.operationId());
                });
                assertThat(abortStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> abort.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                agent.releasePrepare.countDown();
                assertThat(preparation.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
                assertThat(abort.get(10, TimeUnit.SECONDS))
                        .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
                assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
            } finally {
                agent.releasePrepare.countDown();
            }
        }
    }

    @Test
    void committedDecisionIsFinalizedAfterLostAcknowledgement() {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {
            insertMetadata(request.operationId());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    throw new IllegalStateException("Simulated lost commit acknowledgement");
                }
            });
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("acknowledgement");
        assertThat(coordinator.status(request.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
        assertThat(coordinator.recoverCommitted(100)).isEqualTo(1);
        assertThat(coordinator.recoverCommitted(100)).isZero();
        assertThat(metadataCount(request.operationId())).isEqualTo(1);
        assertThat(agent.state).isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
        assertThat(agent.rollbackCalls).isZero();
    }

    @Test
    void newWorkFinalizesCommittedDecisionAfterLostAcknowledgement() {
        var first = request();
        coordinator.prepareAndEnqueue(first);
        assertThatThrownBy(() -> coordinator.process(first.operationId(), () -> {
            insertMetadata(first.operationId());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    throw new IllegalStateException("Simulated lost commit acknowledgement");
                }
            });
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("acknowledgement");
        var next = request();

        assertThat(coordinator.prepareAndEnqueue(next)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
        assertThat(coordinator.recoverCommitted(100)).isZero();
        assertThat(metadataCount(first.operationId())).isEqualTo(1);
        assertThat(coordinator.status(first.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
        assertThat(agent.rollbackCalls).isZero();
        assertThat(coordinator.abort(next.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
        assertThat(jdbc.queryForObject("SELECT operation_id FROM nas_synthetic_write_gate WHERE gate_id = 1",
                String.class)).isNull();
    }

    @Test
    void rowLockPreventsAbortFromUndoingConcurrentCommit() throws Exception {
        var request = request();
        coordinator.prepareAndEnqueue(request);
        agent.applyEntered = new CountDownLatch(1);
        agent.releaseApply = new CountDownLatch(1);
        var abortStarted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var process = pool.submit(() -> coordinator.process(request.operationId(),
                        () -> insertMetadata(request.operationId())));
                assertThat(agent.applyEntered.await(5, TimeUnit.SECONDS)).isTrue();
                var abort = pool.submit(() -> {
                    abortStarted.countDown();
                    return coordinator.abort(request.operationId());
                });
                assertThat(abortStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> abort.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                agent.releaseApply.countDown();
                assertThat(process.get(10, TimeUnit.SECONDS)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
                assertThat(abort.get(10, TimeUnit.SECONDS)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
                assertThat(metadataCount(request.operationId())).isEqualTo(1);
                assertThat(agent.rollbackCalls).isZero();
            } finally {
                agent.releaseApply.countDown();
            }
        }
    }

    private void insertMetadata(UUID operationId) {
        jdbc.update("INSERT INTO synthetic_metadata (operation_id) VALUES (?)", operationId.toString());
    }

    private int metadataCount(UUID operationId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata WHERE operation_id = ?",
                Integer.class, operationId.toString());
    }

    private static NasEpubJobStore.JobRequest request() {
        return new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "output.epub", "0".repeat(64), "New", null))));
    }

    private static final class TestAgent implements NasEpubSyntheticReplacementCoordinator.Agent {
        volatile NasEpubSyntheticReplacement.State state;
        volatile int rollbackCalls;
        boolean losePrepareResponse;
        CountDownLatch applyEntered;
        CountDownLatch releaseApply;
        CountDownLatch prepareEntered;
        CountDownLatch releasePrepare;

        @Override
        public NasEpubSyntheticReplacement.Result prepareReplacement(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException {
            state = NasEpubSyntheticReplacement.State.PREPARED;
            if (prepareEntered != null) {
                prepareEntered.countDown();
                if (!releasePrepare.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for test release");
                }
            }
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
            return new NasEpubSyntheticReplacement.Result(request.operationId(), state);
        }

        @Override
        public NasEpubSyntheticReplacement.Result rollbackReplacement(NasEpubJobStore.JobRequest request) {
            rollbackCalls++;
            state = NasEpubSyntheticReplacement.State.ROLLED_BACK;
            return new NasEpubSyntheticReplacement.Result(request.operationId(), state);
        }

        @Override
        public NasEpubSyntheticReplacement.Result cancelPreparation(NasEpubJobStore.JobRequest request) {
            return rollbackReplacement(request);
        }

        @Override
        public NasEpubSyntheticReplacement.Result finishReplacement(NasEpubJobStore.JobRequest request) throws IOException {
            if (state != NasEpubSyntheticReplacement.State.APPLIED
                    && state != NasEpubSyntheticReplacement.State.COMMITTED) {
                throw new IOException("Synthetic replacement was not applied");
            }
            state = NasEpubSyntheticReplacement.State.COMMITTED;
            return new NasEpubSyntheticReplacement.Result(request.operationId(), state);
        }
    }
}
