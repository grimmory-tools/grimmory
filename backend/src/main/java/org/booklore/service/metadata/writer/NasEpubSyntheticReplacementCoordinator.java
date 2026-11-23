package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NasEpubSyntheticReplacementCoordinator {
    public enum Status { PREPARING, PENDING, COMMITTED, ABORT_PREPARING, ABORTING, ABORTED }

    public interface Agent {
        NasEpubSyntheticReplacement.Result prepareReplacement(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException;
        NasEpubSyntheticReplacement.Result applyReplacement(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException;
        NasEpubSyntheticReplacement.Result rollbackReplacement(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException;
        NasEpubSyntheticReplacement.Result cancelPreparation(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException;
        NasEpubSyntheticReplacement.Result finishReplacement(NasEpubJobStore.JobRequest request)
                throws IOException, InterruptedException;
    }

    private static final int MAX_REQUEST_BYTES = 6 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Agent agent;
    private final NasEpubSyntheticWriteGate gate;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public NasEpubSyntheticReplacementCoordinator(JdbcTemplate jdbc, PlatformTransactionManager manager, Agent agent) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
        this.agent = Objects.requireNonNull(agent);
        this.gate = new NasEpubSyntheticWriteGate(jdbc);
    }

    public Status prepareAndEnqueue(NasEpubJobStore.JobRequest request) {
        requireNoTransaction();
        if (request == null || request.operationId() == null) {
            throw new IllegalArgumentException("An operation ID is required");
        }
        NasEpubPrototype.validateBatch(request.batch());
        String content = serialize(request);
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Synthetic replacement request is too large");
        }
        Row existing = find(request.operationId(), false);
        if (existing != null) {
            matchingStatus(existing, content);
        }
        recoverDecided(1);
        transactions.executeWithoutResult(status -> {
            requireTransaction();
            String owner = gate.lock();
            Row row = find(request.operationId(), true);
            if (row == null) {
                gate.reserve(request.operationId(), owner);
                jdbc.update("INSERT INTO nas_synthetic_replacement_decision (operation_id, request_json, status) "
                                + "VALUES (?, ?, 'PREPARING')",
                        request.operationId().toString(), content);
            } else {
                matchingStatus(row, content);
                requireActiveReservation(request.operationId(), row, owner);
            }
        });
        return transactions.execute(status -> {
            requireTransaction();
            String owner = gate.lock();
            Row row = required(request.operationId(), true);
            Status current = matchingStatus(row, content);
            requireActiveReservation(request.operationId(), row, owner);
            if (current != Status.PREPARING) {
                return current;
            }
            expect(call(() -> agent.prepareReplacement(request)), request.operationId(),
                    NasEpubSyntheticReplacement.State.PREPARED);
            update(request.operationId(), Status.PREPARING, Status.PENDING);
            return Status.PENDING;
        });
    }

    public Status process(UUID operationId, Runnable databaseChanges) {
        requireNoTransaction();
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(databaseChanges);
        try {
            transactions.executeWithoutResult(status -> {
                requireTransaction();
                String owner = gate.lock();
                Row row = required(operationId, true);
                requireActiveReservation(operationId, row, owner);
                NasEpubJobStore.JobRequest request = request(operationId, row);
                if (row.status() != Status.PENDING && row.status() != Status.COMMITTED) {
                    throw new IllegalStateException("Synthetic replacement cannot be processed in state " + row.status());
                }
                if (row.status() == Status.PENDING) {
                    expect(call(() -> agent.applyReplacement(request)), operationId, NasEpubSyntheticReplacement.State.APPLIED);
                    gate.runAsOwner(operationId, databaseChanges);
                    update(operationId, Status.PENDING, Status.COMMITTED);
                }
            });
            if (status(operationId) != Status.COMMITTED) {
                throw new IllegalStateException("Synthetic replacement database decision was not committed");
            }
            finalizeCommitted(operationId);
            return Status.COMMITTED;
        } catch (RuntimeException e) {
            try {
                abort(operationId);
            } catch (RuntimeException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        }
    }

    public Status abort(UUID operationId) {
        requireNoTransaction();
        Objects.requireNonNull(operationId);
        Row decision = transactions.execute(status -> {
            requireTransaction();
            String owner = gate.lock();
            Row row = required(operationId, true);
            requireActiveReservation(operationId, row, owner);
            request(operationId, row);
            if (row.status() == Status.PREPARING || row.status() == Status.PENDING) {
                Status target = row.status() == Status.PREPARING ? Status.ABORT_PREPARING : Status.ABORTING;
                update(operationId, row.status(), target);
                return new Row(row.request(), target, false);
            }
            return row;
        });
        if (decision.status() == Status.COMMITTED || decision.status() == Status.ABORTED) {
            return decision.status();
        }
        if (decision.status() != Status.ABORTING && decision.status() != Status.ABORT_PREPARING) {
            throw new IllegalStateException("Invalid synthetic replacement decision");
        }
        NasEpubJobStore.JobRequest request = request(operationId, decision);
        expect(call(() -> decision.status() == Status.ABORT_PREPARING
                        ? agent.cancelPreparation(request) : agent.rollbackReplacement(request)),
                operationId, NasEpubSyntheticReplacement.State.ROLLED_BACK);
        return transactions.execute(status -> {
            requireTransaction();
            String owner = gate.lock();
            Row current = required(operationId, true);
            if (!current.request().equals(decision.request())) {
                throw new IllegalStateException("Synthetic replacement request changed");
            }
            if (current.status() == decision.status()) {
                gate.release(operationId, owner);
                update(operationId, decision.status(), Status.ABORTED);
                return Status.ABORTED;
            }
            if (current.status() == Status.ABORTED) {
                return Status.ABORTED;
            }
            throw new IllegalStateException("Synthetic replacement decision changed during rollback");
        });
    }

    public Status status(UUID operationId) {
        Objects.requireNonNull(operationId);
        return required(operationId, false).status();
    }

    Optional<Status> statusIfExists(UUID operationId) {
        Row row = find(Objects.requireNonNull(operationId), false);
        return row == null ? Optional.empty() : Optional.of(row.status());
    }

    JdbcTemplate decisionJdbc() {
        return jdbc;
    }

    public int recoverAbandoned(int limit) {
        requireNoTransaction();
        validateRecoveryLimit(limit);
        var operations = jdbc.query("SELECT operation_id FROM nas_synthetic_replacement_decision "
                        + "WHERE status IN ('PREPARING', 'PENDING', 'ABORT_PREPARING', 'ABORTING') ORDER BY operation_id LIMIT ?",
                (rs, row) -> UUID.fromString(rs.getString(1)), limit);
        int recovered = 0;
        for (UUID operationId : operations) {
            if (abort(operationId) == Status.ABORTED) {
                recovered++;
            }
        }
        return recovered;
    }

    public int recoverCommitted(int limit) {
        requireNoTransaction();
        validateRecoveryLimit(limit);
        var operations = jdbc.query("SELECT operation_id FROM nas_synthetic_replacement_decision "
                        + "WHERE status = 'COMMITTED' AND finalized_at IS NULL ORDER BY operation_id LIMIT ?",
                (rs, row) -> UUID.fromString(rs.getString(1)), limit);
        for (UUID operationId : operations) {
            finalizeCommitted(operationId);
        }
        return operations.size();
    }

    public int recoverDecided(int limit) {
        requireNoTransaction();
        validateRecoveryLimit(limit);
        var operations = jdbc.query("SELECT operation_id, status FROM nas_synthetic_replacement_decision "
                        + "WHERE status IN ('ABORT_PREPARING', 'ABORTING') "
                        + "OR (status = 'COMMITTED' AND finalized_at IS NULL) ORDER BY operation_id LIMIT ?",
                (rs, row) -> new RecoveryDecision(UUID.fromString(rs.getString(1)), Status.valueOf(rs.getString(2))), limit);
        for (RecoveryDecision operation : operations) {
            if (operation.status() == Status.COMMITTED) {
                finalizeCommitted(operation.operationId());
            } else {
                abort(operation.operationId());
            }
        }
        return operations.size();
    }

    private void finalizeCommitted(UUID operationId) {
        Row row = transactions.execute(status -> {
            requireTransaction();
            String owner = gate.lock();
            Row current = required(operationId, true);
            if (current.status() != Status.COMMITTED) {
                throw new IllegalStateException("Only a committed synthetic replacement can be finalized");
            }
            requireActiveReservation(operationId, current, owner);
            return current;
        });
        if (row.finalized()) {
            return;
        }
        NasEpubJobStore.JobRequest request = request(operationId, row);
        expect(call(() -> agent.finishReplacement(request)), operationId, NasEpubSyntheticReplacement.State.COMMITTED);
        transactions.executeWithoutResult(status -> {
            requireTransaction();
            String owner = gate.lock();
            Row current = required(operationId, true);
            if (current.status() != Status.COMMITTED || !current.request().equals(row.request())) {
                throw new IllegalStateException("Synthetic replacement decision changed during finalization");
            }
            if (!current.finalized()) {
                gate.release(operationId, owner);
                int changed = jdbc.update("UPDATE nas_synthetic_replacement_decision SET finalized_at = CURRENT_TIMESTAMP "
                                + "WHERE operation_id = ? AND status = 'COMMITTED' AND finalized_at IS NULL",
                        operationId.toString());
                if (changed != 1) {
                    throw new IllegalStateException("Failed to record synthetic replacement finalization");
                }
            }
        });
    }

    private void requireActiveReservation(UUID operationId, Row row, String owner) {
        if (row.status() != Status.ABORTED && (row.status() != Status.COMMITTED || !row.finalized())) {
            gate.requireOwner(operationId, owner);
        } else if (operationId.toString().equals(owner)) {
            throw new IllegalStateException("Finished synthetic replacement still holds the write gate");
        }
    }

    private static void validateRecoveryLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
    }

    private Row required(UUID operationId, boolean lock) {
        Row row = find(operationId, lock);
        if (row == null) {
            throw new IllegalArgumentException("Synthetic replacement not found");
        }
        return row;
    }

    private Row find(UUID operationId, boolean lock) {
        var rows = jdbc.query("SELECT request_json, status, finalized_at FROM nas_synthetic_replacement_decision "
                        + "WHERE operation_id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Row(rs.getString(1), Status.valueOf(rs.getString(2)),
                        rs.getTimestamp(3) != null), operationId.toString());
        if (rows.size() > 1) {
            throw new IllegalStateException("Duplicate synthetic replacement decision");
        }
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void update(UUID operationId, Status from, Status to) {
        int changed = jdbc.update("UPDATE nas_synthetic_replacement_decision SET status = ? "
                        + "WHERE operation_id = ? AND status = ?",
                to.name(), operationId.toString(), from.name());
        if (changed != 1) {
            throw new IllegalStateException("Failed to update synthetic replacement decision");
        }
    }

    private Status matchingStatus(Row row, String request) {
        if (!Objects.equals(row.request(), request)) {
            throw new IllegalStateException("Operation ID already belongs to a different request");
        }
        return row.status();
    }

    private NasEpubJobStore.JobRequest request(UUID operationId, Row row) {
        if (row.request() == null || row.request().getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            throw new IllegalStateException("Invalid stored synthetic replacement request");
        }
        try {
            var request = mapper.readValue(row.request(), NasEpubJobStore.JobRequest.class);
            if (request == null || !operationId.equals(request.operationId())) {
                throw new IllegalStateException("Stored synthetic replacement ID does not match");
            }
            NasEpubPrototype.validateBatch(request.batch());
            return request;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored synthetic replacement request", e);
        }
    }

    private String serialize(NasEpubJobStore.JobRequest request) {
        try {
            return mapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize synthetic replacement request", e);
        }
    }

    private NasEpubSyntheticReplacement.Result call(AgentCall operation) {
        try {
            NasEpubSyntheticReplacement.Result result = operation.execute();
            if (result == null || result.operationId() == null || result.state() == null) {
                throw new IllegalStateException("Invalid synthetic replacement agent response");
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Synthetic replacement agent request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Synthetic replacement agent request failed; no local fallback", e);
        }
    }

    private static void expect(NasEpubSyntheticReplacement.Result result, UUID operationId,
                               NasEpubSyntheticReplacement.State state) {
        if (!operationId.equals(result.operationId()) || result.state() != state) {
            throw new IllegalStateException("Unexpected synthetic replacement agent response");
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(jdbc.getDataSource()))) {
            throw new IllegalStateException("A writable transaction on the decision database is required");
        }
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Synthetic replacement coordination must own its database transaction");
        }
    }

    @FunctionalInterface
    private interface AgentCall {
        NasEpubSyntheticReplacement.Result execute() throws IOException, InterruptedException;
    }

    private record Row(String request, Status status, boolean finalized) {}
    private record RecoveryDecision(UUID operationId, Status status) {}
}
