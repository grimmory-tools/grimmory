package org.booklore.service.metadata.writer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class NasEpubSyntheticWriteGate {
    private static final ThreadLocal<Map<DataSource, Object>> HELD_TRANSACTIONS = new ThreadLocal<>();
    private static final ThreadLocal<Owner> ACTIVE_OWNER = new ThreadLocal<>();
    private final JdbcTemplate jdbc;

    NasEpubSyntheticWriteGate(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    String lock() {
        DataSource source = Objects.requireNonNull(jdbc.getDataSource());
        Object connectionHolder = TransactionSynchronizationManager.getResource(source);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || connectionHolder == null) {
            throw new IllegalStateException("A writable transaction on the decision database is required");
        }
        trackTransaction(source, connectionHolder);
        var rows = jdbc.query("SELECT operation_id FROM nas_synthetic_write_gate WHERE gate_id = 1 FOR UPDATE",
                (rs, row) -> new Reservation(rs.getString(1)));
        if (rows.size() != 1) {
            throw new IllegalStateException("Synthetic write gate is missing or duplicated");
        }
        return rows.getFirst().operationId();
    }

    private static void trackTransaction(DataSource source, Object connectionHolder) {
        Map<DataSource, Object> held = HELD_TRANSACTIONS.get();
        if (held != null && held.containsKey(source)) {
            if (held.get(source) != connectionHolder) {
                throw new IllegalStateException("Synthetic write gate cannot be taken in a nested independent transaction");
            }
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Synthetic write gate requires transaction synchronization");
        }
        if (held == null) {
            held = new IdentityHashMap<>();
            HELD_TRANSACTIONS.set(held);
        }
        Map<DataSource, Object> tracked = held;
        tracked.put(source, connectionHolder);
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (tracked.get(source) == connectionHolder) {
                        tracked.remove(source);
                    }
                    if (tracked.isEmpty() && HELD_TRANSACTIONS.get() == tracked) {
                        HELD_TRANSACTIONS.remove();
                    }
                }
            });
        } catch (RuntimeException e) {
            tracked.remove(source);
            if (tracked.isEmpty()) {
                HELD_TRANSACTIONS.remove();
            }
            throw e;
        }
    }

    void requireAvailable() {
        String current = lock();
        if (current != null) {
            Owner owner = ACTIVE_OWNER.get();
            DataSource source = Objects.requireNonNull(jdbc.getDataSource());
            if (owner != null && owner.source() == source
                    && owner.connectionHolder() == TransactionSynchronizationManager.getResource(source)
                    && owner.operationId().toString().equals(current)) {
                return;
            }
            throw new IllegalStateException("Synthetic sandbox is reserved for replacement");
        }
    }

    void runAsOwner(UUID operationId, Runnable work) {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(work);
        if (ACTIVE_OWNER.get() != null) {
            throw new IllegalStateException("Synthetic write gate owner is already active");
        }
        requireOwner(operationId, lock());
        DataSource source = Objects.requireNonNull(jdbc.getDataSource());
        ACTIVE_OWNER.set(new Owner(source, TransactionSynchronizationManager.getResource(source), operationId));
        try {
            work.run();
        } finally {
            ACTIVE_OWNER.remove();
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
    private record Owner(DataSource source, Object connectionHolder, UUID operationId) {}
}
