package org.booklore.service.metadata.writer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
final class NasEpubSyntheticMaintenanceRunner {
    record Work(long bookId, NasEpubPrototype.Request file, Runnable databaseChanges) {
        Work {
            if (bookId <= 0) {
                throw new IllegalArgumentException("A positive book ID is required");
            }
            Objects.requireNonNull(file);
            Objects.requireNonNull(databaseChanges);
        }
    }

    record Result(long bookId, UUID operationId, boolean succeeded, RuntimeException failure) {}
    enum ReportStatus { STARTED, SUCCEEDED, FAILED }
    record Report(long bookId, UUID operationId, ReportStatus status, String error) {}

    private final NasEpubSyntheticReplacementCoordinator coordinator;
    private final JdbcTemplate jdbc;

    NasEpubSyntheticMaintenanceRunner(NasEpubSyntheticReplacementCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator);
        this.jdbc = coordinator.decisionJdbc();
    }

    void run(UUID runId, Iterable<Work> work, Consumer<Result> report) {
        requireNoTransaction();
        Objects.requireNonNull(runId);
        Objects.requireNonNull(work);
        Objects.requireNonNull(report);
        for (Work item : work) {
            Objects.requireNonNull(item);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO nas_synthetic_maintenance_report "
                            + "(run_id, book_id, operation_id, status) VALUES (?, ?, ?, 'STARTED')",
                    runId.toString(), item.bookId(), id.toString());
            Result result = process(item, id);
            finishReport(runId, item.bookId(), id, result.succeeded(),
                    result.failure() == null ? null : failureMessage(result.failure()));
            report.accept(result);
        }
    }

    // STARTED does not prove abandonment; every producer must be stopped before recovery.
    int recoverInterrupted(UUID runId, int limit) {
        requireNoTransaction();
        Objects.requireNonNull(runId);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Recovery limit must be between 1 and 100");
        }
        var started = jdbc.query("SELECT book_id, operation_id FROM nas_synthetic_maintenance_report "
                        + "WHERE run_id = ? AND status = 'STARTED' ORDER BY book_id LIMIT ?",
                (rs, row) -> new Report(rs.getLong(1), UUID.fromString(rs.getString(2)),
                        ReportStatus.STARTED, null), runId.toString(), limit);
        for (Report item : started) {
            var decision = coordinator.statusIfExists(item.operationId());
            if (decision.isEmpty()) {
                finishReport(runId, item.bookId(), item.operationId(), false, "Interrupted before preparation");
                continue;
            }
            var status = decision.get();
            if (status != NasEpubSyntheticReplacementCoordinator.Status.COMMITTED
                    && status != NasEpubSyntheticReplacementCoordinator.Status.ABORTED) {
                status = coordinator.abort(item.operationId());
            }
            if (status == NasEpubSyntheticReplacementCoordinator.Status.COMMITTED) {
                finalizeCommitted(item.operationId());
                finishReport(runId, item.bookId(), item.operationId(), true, null);
            } else if (status == NasEpubSyntheticReplacementCoordinator.Status.ABORTED) {
                finishReport(runId, item.bookId(), item.operationId(), false, "Interrupted; synthetic work cancelled or restored");
            } else {
                throw new IllegalStateException("Synthetic book decision remains unresolved: " + status);
            }
        }
        return started.size();
    }

    List<Report> reports(UUID runId, long afterBookId, int limit) {
        Objects.requireNonNull(runId);
        if (afterBookId < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Invalid report page");
        }
        return jdbc.query("SELECT book_id, operation_id, status, error_message FROM nas_synthetic_maintenance_report "
                        + "WHERE run_id = ? AND book_id > ? ORDER BY book_id LIMIT ?",
                (rs, row) -> new Report(rs.getLong(1), UUID.fromString(rs.getString(2)),
                        ReportStatus.valueOf(rs.getString(3)), rs.getString(4)), runId.toString(), afterBookId, limit);
    }

    private Result process(Work item, UUID id) {
        var request = new NasEpubJobStore.JobRequest(id, new NasEpubPrototype.BatchRequest(List.of(item.file())));
        try {
            NasEpubPrototype.validateBatch(request.batch());
        } catch (IllegalArgumentException failure) {
            return new Result(item.bookId(), id, false, failure);
        }
        try {
            var prepared = coordinator.prepareAndEnqueue(request);
            if (prepared != NasEpubSyntheticReplacementCoordinator.Status.PENDING) {
                throw new IllegalStateException("Unexpected synthetic preparation state: " + prepared);
            }
            coordinator.process(id, item.databaseChanges());
            return new Result(item.bookId(), id, true, null);
        } catch (RuntimeException failure) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure;
            }
            try {
                var status = coordinator.status(id);
                if (status != NasEpubSyntheticReplacementCoordinator.Status.COMMITTED
                        && status != NasEpubSyntheticReplacementCoordinator.Status.ABORTED) {
                    status = coordinator.abort(id);
                }
                if (status == NasEpubSyntheticReplacementCoordinator.Status.ABORTED) {
                    return new Result(item.bookId(), id, false, failure);
                }
                if (status == NasEpubSyntheticReplacementCoordinator.Status.COMMITTED) {
                    finalizeCommitted(id);
                    log.warn("Synthetic book {} committed despite an earlier error; finalization recovered", item.bookId(), failure);
                    return new Result(item.bookId(), id, true, null);
                }
                throw new IllegalStateException("Synthetic book decision remains unresolved: " + status);
            } catch (RuntimeException recoveryFailure) {
                failure.addSuppressed(recoveryFailure);
                throw failure;
            }
        }

    }

    private void finalizeCommitted(UUID id) {
        coordinator.process(id, () -> {
            throw new IllegalStateException("Committed database changes must not be replayed");
        });
    }

    private void finishReport(UUID runId, long bookId, UUID operationId, boolean succeeded, String error) {
        int changed = jdbc.update("UPDATE nas_synthetic_maintenance_report SET status = ?, error_message = ? "
                        + "WHERE run_id = ? AND book_id = ? AND operation_id = ? AND status = 'STARTED'",
                succeeded ? "SUCCEEDED" : "FAILED", error, runId.toString(), bookId, operationId.toString());
        if (changed != 1) {
            throw new IllegalStateException("Synthetic maintenance report was changed or removed");
        }
    }

    private static String failureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.codePoints().limit(512)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Synthetic maintenance must start outside a transaction");
        }
    }
}
