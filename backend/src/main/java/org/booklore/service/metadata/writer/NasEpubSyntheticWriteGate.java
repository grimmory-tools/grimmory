package org.booklore.service.metadata.writer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.UUID;

final class NasEpubSyntheticWriteGate {
    private final JdbcTemplate jdbc;

    NasEpubSyntheticWriteGate(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    String lock() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(jdbc.getDataSource()))) {
            throw new IllegalStateException("A writable transaction on the decision database is required");
        }
        var rows = jdbc.query("SELECT operation_id FROM nas_synthetic_write_gate WHERE gate_id = 1 FOR UPDATE",
                (rs, row) -> new Reservation(rs.getString(1)));
        if (rows.size() != 1) {
            throw new IllegalStateException("Synthetic write gate is missing or duplicated");
        }
        return rows.getFirst().operationId();
    }

    void requireAvailable() {
        if (lock() != null) {
            throw new IllegalStateException("Synthetic sandbox is reserved for replacement");
        }
    }

    void reserve(UUID operationId, String current) {
        if (current != null) {
            throw new IllegalStateException("Synthetic sandbox is reserved for replacement");
        }
        int changed = jdbc.update("UPDATE nas_synthetic_write_gate SET operation_id = ? "
                        + "WHERE gate_id = 1 AND operation_id IS NULL", operationId.toString());
        if (changed != 1) {
            throw new IllegalStateException("Failed to reserve synthetic write gate");
        }
    }

    void requireOwner(UUID operationId, String current) {
        if (!operationId.toString().equals(current)) {
            throw new IllegalStateException("Synthetic write gate reservation does not match the operation");
        }
    }

    void release(UUID operationId, String current) {
        requireOwner(operationId, current);
        int changed = jdbc.update("UPDATE nas_synthetic_write_gate SET operation_id = NULL "
                        + "WHERE gate_id = 1 AND operation_id = ?", operationId.toString());
        if (changed != 1) {
            throw new IllegalStateException("Failed to release synthetic write gate");
        }
    }

    private record Reservation(String operationId) {}
}
