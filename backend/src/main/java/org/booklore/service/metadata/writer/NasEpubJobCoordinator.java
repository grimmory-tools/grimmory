package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class NasEpubJobCoordinator {
    private static final int MAX_REQUEST_BYTES = 6 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final JobExecutor agent;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @FunctionalInterface
    public interface JobExecutor {
        NasEpubJobStore.JobResult execute(NasEpubJobStore.JobRequest request) throws IOException, InterruptedException;
    }

    public NasEpubJobCoordinator(JdbcTemplate jdbc, PlatformTransactionManager manager, JobExecutor agent) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        this.agent = Objects.requireNonNull(agent);
    }

    public void enqueue(NasEpubJobStore.JobRequest request) {
        requireTransaction();
        if (request == null || request.operationId() == null) {
            throw new IllegalArgumentException("An operation ID is required");
        }
        NasEpubPrototype.validateBatch(request.batch());
        byte[] content = serialize(request);
        if (content.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request is too large");
        }
        jdbc.update("INSERT INTO nas_synthetic_job (operation_id, request_json, status) VALUES (?, ?, 'PENDING')",
                request.operationId().toString(), new String(content, java.nio.charset.StandardCharsets.UTF_8));
    }

    public NasEpubJobStore.JobResult process(UUID operationId) {
        Objects.requireNonNull(operationId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Synthetic jobs must be processed after the enqueue transaction commits");
        }
        return transactions.execute(status -> {
            requireTransaction();
            var rows = jdbc.query("SELECT request_json, result_json, status FROM nas_synthetic_job WHERE operation_id = ? FOR UPDATE",
                    (rs, row) -> new JobRow(rs.getString(1), rs.getString(2), rs.getString(3)),
                    operationId.toString());
            if (rows.size() != 1) {
                throw new IllegalArgumentException("Synthetic job not found");
            }
            JobRow row = rows.getFirst();
            NasEpubJobStore.JobRequest request = deserialize(row.request(), NasEpubJobStore.JobRequest.class);
            if (!operationId.equals(request.operationId())) {
                throw new IllegalStateException("Synthetic job ID does not match its stored request");
            }
            if ("DONE".equals(row.status())) {
                NasEpubJobStore.JobResult result = deserialize(row.result(), NasEpubJobStore.JobResult.class);
                validateResult(request, result);
                NasEpubJobStore.JobResult verified = callAgent(request);
                validateResult(request, verified);
                if (!result.equals(verified)) {
                    throw new IllegalStateException("Prepared synthetic job no longer matches the database");
                }
                return result;
            }
            if (!"PENDING".equals(row.status())) {
                throw new IllegalStateException("Invalid synthetic job status");
            }
            return prepare(request);
        });
    }

    public NasEpubJobStore.JobResult prepareAtomically(NasEpubJobStore.JobRequest request, Runnable databaseChanges) {
        Objects.requireNonNull(databaseChanges);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Synthetic preparation must own its database transaction");
        }
        return transactions.execute(status -> {
            requireTransaction();
            databaseChanges.run();
            enqueue(request);
            return prepare(request);
        });
    }

    public List<UUID> pending(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        return jdbc.query("SELECT operation_id FROM nas_synthetic_job WHERE status = 'PENDING' ORDER BY created_at, operation_id LIMIT ?",
                (rs, row) -> UUID.fromString(rs.getString(1)), limit);
    }

    public int recoverPending(int limit) {
        int recovered = 0;
        for (UUID operationId : pending(limit)) {
            process(operationId);
            recovered++;
        }
        return recovered;
    }

    private NasEpubJobStore.JobResult prepare(NasEpubJobStore.JobRequest request) {
        NasEpubJobStore.JobResult result = callAgent(request);
        validateResult(request, result);
        int updated = jdbc.update("UPDATE nas_synthetic_job SET result_json = ?, status = 'DONE', completed_at = CURRENT_TIMESTAMP WHERE operation_id = ?",
                new String(serialize(result), java.nio.charset.StandardCharsets.UTF_8), request.operationId().toString());
        if (updated != 1) {
            throw new IllegalStateException("Failed to update synthetic job");
        }
        return result;
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(jdbc.getDataSource()))) {
            throw new IllegalStateException("A writable transaction on the job database is required");
        }
    }

    private NasEpubJobStore.JobResult callAgent(NasEpubJobStore.JobRequest request) {
        NasEpubJobStore.JobResult result;
        try {
            result = agent.execute(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Synthetic agent request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Synthetic agent request failed; no local fallback", e);
        }
        return result;
    }

    private void validateResult(NasEpubJobStore.JobRequest request, NasEpubJobStore.JobResult result) {
        if (result == null || !request.operationId().equals(result.operationId()) || result.batch() == null
                || result.batch().results() == null
                || result.batch().results().size() != request.batch().requests().size()) {
            throw new IllegalStateException("Invalid synthetic agent response");
        }
        for (int i = 0; i < result.batch().results().size(); i++) {
            var output = result.batch().results().get(i);
            var item = request.batch().requests().get(i);
            if (output == null || !item.output().equals(output.output()) || output.outputSha256() == null
                    || !output.outputSha256().matches("[a-f0-9]{64}")) {
                throw new IllegalStateException("Invalid synthetic agent output");
            }
        }
    }

    private byte[] serialize(Object object) {
        try {
            return mapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize synthetic job", e);
        }
    }

    private <T> T deserialize(String value, Class<T> type) {
        if (value == null) {
            throw new IllegalStateException("Missing synthetic job data");
        }
        try {
            return mapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid synthetic job data", e);
        }
    }

    private record JobRow(String request, String result, String status) {}
}
