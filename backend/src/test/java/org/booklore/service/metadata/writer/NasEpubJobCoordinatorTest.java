package org.booklore.service.metadata.writer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasEpubJobCoordinatorTest {
    @TempDir
    Path sandbox;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private TransactionTemplate tx;

    @BeforeEach
    void database() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/nas-synthetic-job.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE synthetic_metadata (title VARCHAR(255) NOT NULL)");
        manager = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(manager);
    }

    @Test
    void syntheticSchemaIsNotAnAutomaticFlywayMigration() {
        assertThat(new ClassPathResource("db/migration/V153__Create_nas_synthetic_job_table.sql").exists())
                .isFalse();
    }

    @Test
    void rollsBackEnqueueWithDatabaseTransaction() throws Exception {
        var request = request();
        var coordinator = new NasEpubJobCoordinator(jdbc, manager, job -> {
            throw new AssertionError("Agent must not run while enqueuing");
        });
        assertThatThrownBy(() -> coordinator.enqueue(request)).isInstanceOf(IllegalStateException.class);
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO synthetic_metadata (title) VALUES ('changed')");
            coordinator.enqueue(request);
            assertThatThrownBy(() -> coordinator.process(request.operationId()))
                    .isInstanceOf(IllegalStateException.class);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class)).isZero();
        assertThat(coordinator.pending(100)).isEmpty();
    }

    @Test
    void refusesTransactionManagerForDifferentDatabaseBeforeRunningChanges() throws Exception {
        var request = request();
        var otherDataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        otherDataSource.setDriverClassName("org.h2.Driver");
        var otherManager = new DataSourceTransactionManager(otherDataSource);
        var coordinator = new NasEpubJobCoordinator(jdbc, otherManager, job -> {
            throw new AssertionError("Agent must not run");
        });
        AtomicBoolean changed = new AtomicBoolean();
        assertThatThrownBy(() -> coordinator.prepareAtomically(request, () -> changed.set(true)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("job database");
        assertThat(changed).isFalse();
    }

    @Test
    void retriesAfterLostResponseWithoutRepeatingCompletedOperation() throws Exception {
        var request = request();
        AtomicInteger calls = new AtomicInteger();
        var store = new NasEpubJobStore();
        NasEpubJobCoordinator.JobExecutor agent = job -> {
            var result = store.execute(sandbox, job);
            if (calls.getAndIncrement() == 0) {
                throw new IOException("Simulated response loss");
            }
            return result;
        };
        var coordinator = new NasEpubJobCoordinator(jdbc, manager, agent);
        tx.executeWithoutResult(status -> coordinator.enqueue(request));
        assertThatThrownBy(() -> coordinator.process(request.operationId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
        assertThat(coordinator.pending(100)).containsExactly(request.operationId());

        var afterRestart = new NasEpubJobCoordinator(jdbc, manager, agent);
        var result = afterRestart.process(request.operationId());
        assertThat(result.operationId()).isEqualTo(request.operationId());
        assertThat(afterRestart.pending(100)).isEmpty();
        assertThat(afterRestart.process(request.operationId())).isEqualTo(result);
        assertThat(calls).hasValue(3);
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
    }

    @Test
    void agentFailureRollsBackMetadataAndJob() throws Exception {
        var request = request();
        var coordinator = new NasEpubJobCoordinator(jdbc, manager, job -> {
            throw new IOException("Agent unavailable");
        });
        assertThatThrownBy(() -> coordinator.prepareAtomically(request,
                () -> jdbc.update("INSERT INTO synthetic_metadata (title) VALUES ('changed')")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("agent request failed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class)).isZero();
        assertThat(coordinator.pending(100)).isEmpty();
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
    }

    @Test
    void completedJobIsNotReportedAsAvailableWhenAgentIsOffline() throws Exception {
        var request = request();
        AtomicBoolean offline = new AtomicBoolean();
        var store = new NasEpubJobStore();
        var coordinator = new NasEpubJobCoordinator(jdbc, manager, job -> {
            if (offline.get()) {
                throw new IOException("Agent unavailable");
            }
            return store.execute(sandbox, job);
        });
        tx.executeWithoutResult(status -> coordinator.enqueue(request));
        coordinator.process(request.operationId());
        offline.set(true);
        assertThatThrownBy(() -> coordinator.process(request.operationId()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
    }

    @Test
    void retriesPreparedCandidateAfterDatabaseCommitFails() throws Exception {
        var request = request();
        var store = new NasEpubJobStore();
        var coordinator = new NasEpubJobCoordinator(jdbc, manager, job -> store.execute(sandbox, job));
        assertThatThrownBy(() -> coordinator.prepareAtomically(request, () -> {
            jdbc.update("INSERT INTO synthetic_metadata (title) VALUES ('changed')");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("Simulated database commit failure");
                }
            });
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("commit failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class)).isZero();
        assertThat(coordinator.pending(100)).isEmpty();
        var result = coordinator.prepareAtomically(request,
                () -> jdbc.update("INSERT INTO synthetic_metadata (title) VALUES ('changed')"));
        assertThat(result.operationId()).isEqualTo(request.operationId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class)).isEqualTo(1);
        assertThat(coordinator.process(request.operationId())).isEqualTo(result);
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
    }

    @Test
    void recoveryStopsOnFailureAndResumesPendingJobs() throws Exception {
        var first = request();
        var second = new NasEpubJobStore.JobRequest(UUID.randomUUID(), first.batch());
        var store = new NasEpubJobStore();
        AtomicInteger calls = new AtomicInteger();
        var coordinator = new NasEpubJobCoordinator(jdbc, manager, job -> {
            if (calls.getAndIncrement() == 0) {
                throw new IOException("Agent unavailable");
            }
            return store.execute(sandbox, job);
        });
        tx.executeWithoutResult(status -> {
            coordinator.enqueue(first);
            coordinator.enqueue(second);
        });
        assertThatThrownBy(() -> coordinator.recoverPending(100))
                .isInstanceOf(IllegalStateException.class);
        assertThat(coordinator.pending(100)).containsExactlyInAnyOrder(first.operationId(), second.operationId());
        assertThat(coordinator.recoverPending(100)).isEqualTo(2);
        assertThat(coordinator.pending(100)).isEmpty();
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
    }

    @Test
    void rowLockPreventsConcurrentExecutionOfSameJob() throws Exception {
        var request = request();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var coordinator = new NasEpubJobCoordinator(jdbc, manager, job -> {
            calls.incrementAndGet();
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for test release");
            }
            return new NasEpubJobStore.JobResult(job.operationId(),
                    new NasEpubPrototype.BatchResult(List.of(new NasEpubPrototype.Result("output.epub", "a".repeat(64)))));
        });
        tx.executeWithoutResult(status -> coordinator.enqueue(request));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> coordinator.process(request.operationId()));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> coordinator.process(request.operationId()));
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(calls).hasValue(2);
    }

    private NasEpubJobStore.JobRequest request() throws Exception {
        Path input = sandbox.resolve("input.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(input))) {
            entry(zip, "mimetype", "application/epub+zip");
            entry(zip, "META-INF/grimmory-nas-prototype.txt", "synthetic-test-only-v1");
            entry(zip, "META-INF/container.xml", """
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles>
                    </container>""");
            entry(zip, "OPS/book.opf", """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Old</dc:title></metadata>
                      <manifest/><spine/>
                    </package>""");
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        var item = new NasEpubPrototype.Request("input.epub", "output.epub", hash, "New", null);
        return new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(item)));
    }

    private void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
