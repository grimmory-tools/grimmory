package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NasEpubAgentTest {
    private static final char[] PASSWORD = "test-password".toCharArray();
    @TempDir
    static Path certificates;
    @TempDir
    Path sandbox;
    private static SSLContext serverTls;
    private static SSLContext clientTls;
    private static SSLContext unauthorizedTls;
    private static SSLContext noCertificateTls;

    @BeforeAll
    static void certificates() throws Exception {
        generateKey("server");
        generateKey("client");
        generateKey("unauthorized");
        exportCertificate("server");
        exportCertificate("client");
        importCertificate("client", "server-trust");
        importCertificate("server", "client-trust");
        serverTls = context("server", "server-trust");
        clientTls = context("client", "client-trust");
        unauthorizedTls = context("unauthorized", "client-trust");
        noCertificateTls = context("client-trust", "client-trust");
    }

    @Test
    void acceptsOnlyAuthenticatedSyntheticRequests() throws Exception {
        Path input = createSynthetic("input.epub");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        String request = new ObjectMapper().writeValueAsString(
                new NasEpubPrototype.Request("input.epub", "output.epub", hash, "New", List.of("Author")));
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox);
        try {
            URI endpoint = URI.create("https://localhost:" + server.getAddress().getPort() + "/v1/synthetic-epub");
            assertThatThrownBy(() -> send(unauthorizedTls, endpoint, request))
                    .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> send(noCertificateTls, endpoint, request))
                    .isInstanceOf(IOException.class);
            assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
            NasEpubAgentClient client = new NasEpubAgentClient(endpoint, clientTls, Duration.ofSeconds(15));
            var result = client.write(new ObjectMapper().readValue(request, NasEpubPrototype.Request.class));
            assertThat(result.output()).isEqualTo("output.epub");
            assertThat(result.outputSha256()).matches("[a-f0-9]{64}");
            assertThat(Files.exists(sandbox.resolve("output.epub"))).isTrue();
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input))))
                    .isEqualTo(hash);
            assertThatThrownBy(() -> client.write(new ObjectMapper().readValue(request, NasEpubPrototype.Request.class)))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            Path unmarked = sandbox.resolve("unmarked.epub");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(unmarked))) {
                entry(zip, "mimetype", "application/epub+zip");
            }
            String unmarkedHash = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(unmarked)));
            var unmarkedRequest = new NasEpubPrototype.Request(
                    "unmarked.epub", "unmarked-output.epub", unmarkedHash, "New", null);
            assertThatThrownBy(() -> client.write(unmarkedRequest))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThat(Files.exists(sandbox.resolve("unmarked-output.epub"))).isFalse();
            Files.copy(input, sandbox.resolve("input2.epub"));
            var one = new NasEpubPrototype.Request("input.epub", "batch1.epub", hash, "New", null);
            var two = new NasEpubPrototype.Request("input2.epub", "batch2.epub", hash, "New", null);
            var batchResult = client.writeBatch(new NasEpubPrototype.BatchRequest(List.of(one, two)));
            assertThat(batchResult.results()).extracting(NasEpubPrototype.Result::output)
                    .containsExactly("batch1.epub", "batch2.epub");
            var badOne = new NasEpubPrototype.Request("input.epub", "rollback1.epub", hash, "New", null);
            var badTwo = new NasEpubPrototype.Request("input2.epub", "rollback2.epub", "0".repeat(64), "New", null);
            assertThatThrownBy(() -> client.writeBatch(new NasEpubPrototype.BatchRequest(List.of(badOne, badTwo))))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThat(Files.exists(sandbox.resolve("rollback1.epub"))).isFalse();
            assertThat(Files.exists(sandbox.resolve("rollback2.epub"))).isFalse();
            UUID operationId = UUID.randomUUID();
            var job = new NasEpubJobStore.JobRequest(operationId,
                    new NasEpubPrototype.BatchRequest(List.of(
                            new NasEpubPrototype.Request("input.epub", "prepared.epub", hash, "New", null))));
            var jobResult = client.runJob(job);
            assertThat(client.runJob(job)).isEqualTo(jobResult);
            assertThat(Files.exists(sandbox.resolve("prepared.epub"))).isFalse();
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs")
                    .resolve(operationId.toString()).resolve("prepared.epub"))).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void replacementIsDisabledByDefault() throws Exception {
        Path input = createSynthetic("input.epub");
        String original = NasEpubPrototype.sha256(input);
        var job = replacementJob(original);
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox);
        try {
            var client = client(server);
            assertThatThrownBy(() -> client.prepareReplacement(job))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 404");
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs"))).isFalse();
        } finally {
            server.stop(0);
        }
        assertThat(NasEpubAgent.syntheticReplacementEnabled(null)).isFalse();
        assertThat(NasEpubAgent.syntheticReplacementEnabled("false")).isFalse();
        assertThat(NasEpubAgent.syntheticReplacementEnabled("true")).isTrue();
        assertThatThrownBy(() -> NasEpubAgent.syntheticReplacementEnabled("enabled"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NasEpubAgent.start(
                new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, false, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires synthetic replacement");
    }

    @Test
    void authenticatedReplacementCanRecoverAfterAgentRestart() throws Exception {
        Path input = createSynthetic("input.epub");
        Path other = createSynthetic("other.epub");
        String original = NasEpubPrototype.sha256(input);
        var job = replacementJob(original);
        var competitor = new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("other.epub", "other-prepared.epub",
                        NasEpubPrototype.sha256(other), "New other", null))));
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            URI prepare = URI.create("https://localhost:" + server.getAddress().getPort()
                    + "/v1/synthetic-replacement/prepare");
            String body = new ObjectMapper().writeValueAsString(job);
            assertThatThrownBy(() -> send(unauthorizedTls, prepare, body)).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> send(noCertificateTls, prepare, body)).isInstanceOf(IOException.class);
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs"))).isFalse();
            var client = client(server);
            assertThat(client.prepareReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
            assertThat(client.applyReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
            assertThat(client.applyReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
            assertThat(NasEpubPrototype.sha256(input)).isNotEqualTo(original);
        } finally {
            server.stop(0);
        }
        HttpsServer restarted = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var client = client(restarted);
            assertThatThrownBy(() -> client.prepareReplacement(competitor))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThat(client.rollbackReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
            assertThat(client.rollbackReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
            assertThat(client.prepareReplacement(competitor).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
            assertThat(client.rollbackReplacement(competitor).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        } finally {
            restarted.stop(0);
        }
    }

    @Test
    void authenticatedReplacementRecoversFromSeparateStateDirectory(@TempDir Path state) throws Exception {
        Path input = createSynthetic("input.epub");
        String original = NasEpubPrototype.sha256(input);
        var job = replacementJob(original);
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls,
                sandbox, true, false, state);
        try {
            assertThat(client(server).prepareReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
            assertThat(client(server).applyReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.APPLIED);
            assertThat(Files.exists(state.resolve(job.operationId().toString()).resolve("replacement").resolve("0.bak"))).isTrue();
        } finally {
            server.stop(0);
        }

        HttpsServer restarted = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls,
                sandbox, true, false, state);
        try {
            assertThat(client(restarted).rollbackReplacement(job).state()).isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
        } finally {
            restarted.stop(0);
        }
    }

    @Test
    void replacementEndpointRejectsUnmarkedInputs() throws Exception {
        Path input = sandbox.resolve("input.epub");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(input))) {
            entry(zip, "mimetype", "application/epub+zip");
        }
        String original = NasEpubPrototype.sha256(input);
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            assertThatThrownBy(() -> client(server).prepareReplacement(replacementJob(original)))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void coordinatesSyntheticFileAndDatabaseThroughAuthenticatedAgent() throws Exception {
        Path input = createSynthetic("input.epub");
        String original = NasEpubPrototype.sha256(input);
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE synthetic_metadata (title VARCHAR(255) NOT NULL)");
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var coordinator = new NasEpubSyntheticReplacementCoordinator(
                    jdbc, new DataSourceTransactionManager(source), client(server));
            var failed = replacementJob(original);
            coordinator.prepareAndEnqueue(failed);
            assertThatThrownBy(() -> coordinator.process(failed.operationId(), () -> {
                jdbc.update("INSERT INTO synthetic_metadata VALUES ('failed')");
                throw new IllegalStateException("Simulated database error");
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("database error");
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class)).isZero();
            assertThat(coordinator.abort(failed.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);

            var committed = replacementJob(original);
            coordinator.prepareAndEnqueue(committed);
            assertThat(coordinator.process(committed.operationId(),
                    () -> jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')")))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
            assertThat(coordinator.abort(committed.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
            assertThat(NasEpubPrototype.sha256(input)).isNotEqualTo(original);
            assertThat(client(server).finishReplacement(committed).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.COMMITTED);
            assertThatThrownBy(() -> client(server).rollbackReplacement(committed))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class)).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nextRequestRecoversCommittedReplacementThroughRestartedAgent() throws Exception {
        Path input = createSynthetic("input.epub");
        String original = NasEpubPrototype.sha256(input);
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE synthetic_metadata (title VARCHAR(255) NOT NULL)");
        var request = replacementJob(original);
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var coordinator = new NasEpubSyntheticReplacementCoordinator(
                    jdbc, new DataSourceTransactionManager(source), client(server));
            coordinator.prepareAndEnqueue(request);
            assertThatThrownBy(() -> coordinator.process(request.operationId(), () -> {
                jdbc.update("INSERT INTO synthetic_metadata VALUES ('committed')");
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        throw new IllegalStateException("Simulated lost commit acknowledgement");
                    }
                });
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("acknowledgement");
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation")))
                    .isTrue();
        } finally {
            server.stop(0);
        }

        HttpsServer restarted = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var coordinator = new NasEpubSyntheticReplacementCoordinator(
                    jdbc, new DataSourceTransactionManager(source), client(restarted));
            var next = replacementJob(NasEpubPrototype.sha256(input));
            assertThat(coordinator.prepareAndEnqueue(next)).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PENDING);
            assertThat(coordinator.recoverCommitted(1)).isZero();
            assertThat(coordinator.abort(next.operationId())).isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation")))
                    .isFalse();
            assertThat(NasEpubPrototype.sha256(input)).isNotEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata", Integer.class)).isEqualTo(1);
        } finally {
            restarted.stop(0);
        }
    }

    @Test
    void recoversLostPreparationResponseThroughRestartedAgent() throws Exception {
        Path input = createSynthetic("input.epub");
        String original = NasEpubPrototype.sha256(input);
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var request = replacementJob(original);
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var client = client(server);
            var unreliable = new NasEpubSyntheticReplacementCoordinator.Agent() {
                @Override
                public NasEpubSyntheticReplacement.Result prepareReplacement(NasEpubJobStore.JobRequest job)
                        throws IOException, InterruptedException {
                    client.prepareReplacement(job);
                    throw new IOException("Simulated lost preparation response");
                }

                @Override
                public NasEpubSyntheticReplacement.Result applyReplacement(NasEpubJobStore.JobRequest job)
                        throws IOException, InterruptedException {
                    return client.applyReplacement(job);
                }

                @Override
                public NasEpubSyntheticReplacement.Result rollbackReplacement(NasEpubJobStore.JobRequest job)
                        throws IOException, InterruptedException {
                    return client.rollbackReplacement(job);
                }

                @Override
                public NasEpubSyntheticReplacement.Result cancelPreparation(NasEpubJobStore.JobRequest job)
                        throws IOException, InterruptedException {
                    return client.cancelPreparation(job);
                }

                @Override
                public NasEpubSyntheticReplacement.Result finishReplacement(NasEpubJobStore.JobRequest job)
                        throws IOException, InterruptedException {
                    return client.finishReplacement(job);
                }
            };
            var coordinator = new NasEpubSyntheticReplacementCoordinator(
                    jdbc, new DataSourceTransactionManager(source), unreliable);
            assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("no local fallback");
            assertThat(coordinator.status(request.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PREPARING);
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation")))
                    .isTrue();
        } finally {
            server.stop(0);
        }

        HttpsServer restarted = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var coordinator = new NasEpubSyntheticReplacementCoordinator(
                    jdbc, new DataSourceTransactionManager(source), client(restarted));
            assertThat(coordinator.recoverAbandoned(1)).isEqualTo(1);
            assertThat(coordinator.status(request.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation")))
                    .isFalse();
        } finally {
            restarted.stop(0);
        }
    }

    @Test
    void cancelsUnfinishedPreparationWithoutRequiringMissingInput() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        verifyCancellationOfUnfinishedPreparation(source);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NAS_MARIADB_TEST", matches = "true")
    void cancelsUnfinishedPreparationThroughMariaDbAndMtls() throws Exception {
        verifyCancellationOfUnfinishedPreparation(NasEpubMariaDbCoordinatorTest.isolatedDataSource());
    }

    private void verifyCancellationOfUnfinishedPreparation(DriverManagerDataSource source) throws Exception {
        Path input = createSynthetic("input.epub");
        String original = NasEpubPrototype.sha256(input);
        Path held = sandbox.resolve("held.epub");
        Files.move(input, held);
        var jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        var request = replacementJob(original);
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var client = client(server);
            var coordinator = new NasEpubSyntheticReplacementCoordinator(jdbc, manager, client);
            assertThatThrownBy(() -> coordinator.prepareAndEnqueue(request))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(coordinator.status(request.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.PREPARING);

            Path jobs = sandbox.resolve(".grimmory-synthetic-jobs");
            Files.createDirectory(jobs.resolve(request.operationId().toString()).resolve("replacement"));
            Files.writeString(jobs.resolve("replacement-reservation"), request.operationId().toString());
            assertThatThrownBy(() -> client.rollbackReplacement(request))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThat(Files.exists(jobs.resolve("replacement-reservation"))).isTrue();
            assertThat(coordinator.recoverAbandoned(1)).isEqualTo(1);
            assertThat(coordinator.status(request.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);
            assertThat(Files.exists(jobs.resolve("replacement-reservation"))).isFalse();
            assertThat(jdbc.queryForObject("SELECT operation_id FROM nas_synthetic_write_gate WHERE gate_id = 1",
                    String.class)).isNull();
        } finally {
            server.stop(0);
        }

        Files.move(held, input);
        HttpsServer restarted = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var client = client(restarted);
            assertThatThrownBy(() -> client.prepareReplacement(request))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThatThrownBy(() -> client.runJob(request))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            assertThat(client.cancelPreparation(request).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
            var changed = new NasEpubJobStore.JobRequest(request.operationId(), new NasEpubPrototype.BatchRequest(List.of(
                    new NasEpubPrototype.Request("input.epub", "other.epub", original, "Other", null))));
            assertThatThrownBy(() -> client.cancelPreparation(changed))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            var neverStarted = replacementJob(original);
            assertThat(client.cancelPreparation(neverStarted).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
            Path reservation = sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation");
            Files.writeString(reservation, neverStarted.operationId().toString());
            assertThat(client.cancelPreparation(neverStarted).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
            assertThat(Files.exists(reservation)).isFalse();
            assertThatThrownBy(() -> client.prepareReplacement(neverStarted))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
            var next = replacementJob(original);
            assertThat(client.prepareReplacement(next).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.PREPARED);
            assertThat(client.rollbackReplacement(next).state())
                    .isEqualTo(NasEpubSyntheticReplacement.State.ROLLED_BACK);
        } finally {
            restarted.stop(0);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NAS_MARIADB_TEST", matches = "true")
    void coordinatesAndRecoversSyntheticFilesThroughMariaDbAndMtls() throws Exception {
        verifyDatabaseAndMtlsRecovery(NasEpubMariaDbCoordinatorTest.isolatedDataSource());
    }

    @Test
    void coordinatesAndRecoversSyntheticFilesThroughH2AndMtls() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "");
        source.setDriverClassName("org.h2.Driver");
        new ResourceDatabasePopulator(new ClassPathResource("db/nas-synthetic-job.sql")).execute(source);
        new JdbcTemplate(source).execute("CREATE TABLE synthetic_metadata (operation_id CHAR(36) NOT NULL PRIMARY KEY)");
        verifyDatabaseAndMtlsRecovery(source);
    }

    private void verifyDatabaseAndMtlsRecovery(DriverManagerDataSource source) throws Exception {
        Path input = createSynthetic("input.epub");
        String original = NasEpubPrototype.sha256(input);
        var jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        var failed = replacementJob(original);
        var committed = replacementJob(original);
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var coordinator = new NasEpubSyntheticReplacementCoordinator(jdbc, manager, client(server));
            coordinator.prepareAndEnqueue(failed);
            assertThatThrownBy(() -> coordinator.process(failed.operationId(), () -> {
                jdbc.update("INSERT INTO synthetic_metadata (operation_id) VALUES (?)", failed.operationId().toString());
                throw new IllegalStateException("Simulated database failure");
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("database failure");
            assertThat(NasEpubPrototype.sha256(input)).isEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata WHERE operation_id = ?",
                    Integer.class, failed.operationId().toString())).isZero();
            assertThat(coordinator.status(failed.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.ABORTED);

            coordinator.prepareAndEnqueue(committed);
            assertThatThrownBy(() -> coordinator.process(committed.operationId(), () -> {
                jdbc.update("INSERT INTO synthetic_metadata (operation_id) VALUES (?)", committed.operationId().toString());
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        throw new IllegalStateException("Simulated lost commit acknowledgement");
                    }
                });
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("acknowledgement");
            assertThat(coordinator.status(committed.operationId()))
                    .isEqualTo(NasEpubSyntheticReplacementCoordinator.Status.COMMITTED);
            assertThat(NasEpubPrototype.sha256(input)).isNotEqualTo(original);
        } finally {
            server.stop(0);
        }

        HttpsServer restarted = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox, true);
        try {
            var coordinator = new NasEpubSyntheticReplacementCoordinator(jdbc, manager, client(restarted));
            assertThat(coordinator.recoverCommitted(1)).isEqualTo(1);
            assertThat(coordinator.recoverCommitted(1)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM synthetic_metadata WHERE operation_id = ?",
                    Integer.class, committed.operationId().toString())).isEqualTo(1);
            assertThat(NasEpubPrototype.sha256(input)).isNotEqualTo(original);
            assertThat(jdbc.queryForObject("SELECT operation_id FROM nas_synthetic_write_gate WHERE gate_id = 1",
                    String.class)).isNull();
            assertThat(Files.exists(sandbox.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation")))
                    .isFalse();
            assertThatThrownBy(() -> client(restarted).rollbackReplacement(committed))
                    .isInstanceOf(IOException.class).hasMessageContaining("HTTP 422");
        } finally {
            restarted.stop(0);
        }
    }

    @Test
    void clientRejectsInsecureOrUnexpectedEndpoints() {
        assertThatThrownBy(() -> new NasEpubAgentClient(
                URI.create("http://localhost/v1/synthetic-epub"), clientTls, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NasEpubAgentClient(
                URI.create("https://localhost/v1/synthetic-epub?redirect=1"), clientTls, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unavailableAgentDoesNotWriteLocally() throws Exception {
        Path input = sandbox.resolve("input.epub");
        Files.writeString(input, "unchanged");
        HttpsServer server = NasEpubAgent.start(new InetSocketAddress("127.0.0.1", 0), serverTls, sandbox);
        URI endpoint = URI.create("https://localhost:" + server.getAddress().getPort() + "/v1/synthetic-epub");
        server.stop(0);

        NasEpubAgentClient client = new NasEpubAgentClient(endpoint, clientTls, Duration.ofSeconds(2));
        var request = new NasEpubPrototype.Request("input.epub", "output.epub", "0".repeat(64), "New", null);
        assertThatThrownBy(() -> client.write(request)).isInstanceOf(IOException.class);
        assertThat(Files.readString(input)).isEqualTo("unchanged");
        assertThat(Files.exists(sandbox.resolve("output.epub"))).isFalse();
    }

    private static HttpResponse<String> send(SSLContext tls, URI endpoint, String body) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().sslContext(tls).connectTimeout(Duration.ofSeconds(5)).build();
        return client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private NasEpubAgentClient client(HttpsServer server) {
        URI endpoint = URI.create("https://localhost:" + server.getAddress().getPort() + "/v1/synthetic-epub");
        return new NasEpubAgentClient(endpoint, clientTls, Duration.ofSeconds(15));
    }

    private NasEpubJobStore.JobRequest replacementJob(String hash) {
        return new NasEpubJobStore.JobRequest(UUID.randomUUID(), new NasEpubPrototype.BatchRequest(List.of(
                new NasEpubPrototype.Request("input.epub", "prepared.epub", hash, "New", null))));
    }

    private Path createSynthetic(String name) throws IOException {
        Path input = sandbox.resolve(name);
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
        return input;
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void generateKey(String name) throws Exception {
        keytool("-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=" + name, "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-validity", "1", "-keystore", path(name).toString(), "-storetype", "PKCS12",
                "-storepass", "test-password", "-keypass", "test-password", "-noprompt");
    }

    private static void exportCertificate(String name) throws Exception {
        keytool("-exportcert", "-alias", name, "-keystore", path(name).toString(),
                "-storepass", "test-password", "-file", certificates.resolve(name + ".cer").toString());
    }

    private static void importCertificate(String name, String trustStore) throws Exception {
        keytool("-importcert", "-alias", name, "-file", certificates.resolve(name + ".cer").toString(),
                "-keystore", path(trustStore).toString(), "-storetype", "PKCS12",
                "-storepass", "test-password", "-noprompt");
    }

    private static SSLContext context(String key, String trust) throws Exception {
        return NasEpubAgent.tlsContext(path(key), PASSWORD, path(trust), PASSWORD);
    }

    private static Path path(String name) {
        return certificates.resolve(name + ".p12");
    }

    private static void keytool(String... arguments) throws Exception {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        Path executable = bin.resolve(System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
        var command = new java.util.ArrayList<String>();
        command.add(executable.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }
}
