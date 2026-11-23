package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public final class NasEpubSyntheticReplacement {
    public enum State { PREPARED, APPLYING, APPLIED, COMMITTED, ROLLING_BACK, ROLLED_BACK }
    public record Result(UUID operationId, State state) {}

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final long MAX_EPUB_BYTES = 512L * 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final NasEpubJobStore store;
    private final NasSynologyPermissions permissions;
    private final Runnable afterFirstReplacement;

    NasEpubSyntheticReplacement() {
        this(null);
    }

    NasEpubSyntheticReplacement(NasSynologyPermissions permissions) {
        this(permissions, null);
    }

    NasEpubSyntheticReplacement(NasSynologyPermissions permissions, Runnable afterFirstReplacement) {
        this(permissions, afterFirstReplacement, new NasEpubJobStore());
    }

    NasEpubSyntheticReplacement(NasSynologyPermissions permissions, Runnable afterFirstReplacement, NasEpubJobStore store) {
        this.permissions = permissions;
        this.afterFirstReplacement = afterFirstReplacement;
        this.store = Objects.requireNonNull(store);
    }

    State prepare(Path sandbox, NasEpubJobStore.JobRequest request) throws IOException {
        return locked(sandbox, root -> {
            NasEpubJobStore.JobResult result = store.execute(root, request);
            Path job = job(root, request);
            Path replacement = directory(job.resolve("replacement"));
            NasEpubJobStore.syncDirectory(job);
            List<Entry> entries = entries(request, result);
            Path journal = replacement.resolve("journal.json");
            if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                Journal existing = readJournal(journal, entries);
                if (existing.state() == State.COMMITTED || existing.state() == State.ROLLED_BACK) {
                    releaseReservation(root, request.operationId());
                    return existing.state();
                }
                requireReservation(root, request.operationId());
                Context context = new Context(job, replacement, entries, existing);
                checkBackups(context);
                verifyPermissions(root, context);
                if (existing.state() == State.APPLIED) {
                    for (Entry entry : entries) {
                        checkFile(root.resolve(entry.input()), entry.outputHash());
                    }
                } else {
                    checkTargets(root, entries, existing.state() == State.APPLYING
                            || existing.state() == State.ROLLING_BACK);
                }
                return existing.state();
            }
            acquireReservation(root, request.operationId());
            try {
                List<NasSynologyPermissions.Snapshot> snapshots = new ArrayList<>();
                for (int i = 0; i < entries.size(); i++) {
                    Entry entry = entries.get(i);
                    Path input = root.resolve(entry.input());
                    checkFile(input, entry.originalHash());
                    NasEpubPrototype.validateArchive(input);
                    if (permissions != null) {
                        snapshots.add(permissions.capture(input));
                    }
                    Path backup = replacement.resolve(i + ".bak");
                    if (!Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                        copyDurably(input, backup, replacement, false, null);
                    }
                    checkFile(backup, entry.originalHash());
                    if (permissions != null) {
                        permissions.verify(input, snapshots.get(i));
                    }
                }
                writeJournal(replacement, new Journal(State.PREPARED, entries, List.copyOf(snapshots)));
                return State.PREPARED;
            } catch (IOException | RuntimeException e) {
                if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        releaseReservation(root, request.operationId());
                    } catch (IOException cleanupError) {
                        e.addSuppressed(cleanupError);
                    }
                }
                throw e;
            }
        });
    }

    State apply(Path sandbox, NasEpubJobStore.JobRequest request) throws IOException {
        return locked(sandbox, root -> {
            Context context = context(root, request);
            Journal journal = context.journal();
            if (journal.state() == State.COMMITTED) {
                releaseReservation(root, request.operationId());
                return State.COMMITTED;
            }
            if (journal.state() != State.PREPARED && journal.state() != State.APPLYING
                    && journal.state() != State.APPLIED) {
                throw new IOException("Synthetic replacement cannot be applied in state " + journal.state());
            }
            requireReservation(root, request.operationId());
            if (journal.state() == State.APPLIED) {
                checkBackups(context);
                verifyPermissions(root, context);
                for (Entry entry : context.entries()) {
                    checkFile(root.resolve(entry.input()), entry.outputHash());
                }
                return State.APPLIED;
            }
            checkBackups(context);
            try {
                verifyPermissions(root, context);
                checkTargets(root, context.entries(), journal.state() == State.APPLYING);
                writeState(context, State.APPLYING);
                for (int i = 0; i < context.entries().size(); i++) {
                    Entry entry = context.entries().get(i);
                    Path target = root.resolve(entry.input());
                    String hash = checkedHash(target);
                    if (hash.equalsIgnoreCase(entry.originalHash())) {
                        checkFile(context.job().resolve(entry.output()), entry.outputHash());
                        copyDurably(context.job().resolve(entry.output()), target, root, true, snapshot(context, i));
                        checkFile(target, entry.outputHash());
                        verifyPermission(target, context, i);
                        if (i == 0 && afterFirstReplacement != null) {
                            afterFirstReplacement.run();
                        }
                    } else if (!hash.equalsIgnoreCase(entry.outputHash())) {
                        throw new IOException("Synthetic input changed during replacement");
                    }
                }
                writeState(context, State.APPLIED);
                return State.APPLIED;
            } catch (IOException | RuntimeException e) {
                try {
                    rollbackLocked(root, context);
                    releaseReservation(root, request.operationId());
                } catch (IOException | RuntimeException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        });
    }

    State rollback(Path sandbox, NasEpubJobStore.JobRequest request) throws IOException {
        return locked(sandbox, root -> rollbackWithJournal(root, request));
    }

    State cancelPreparation(Path sandbox, NasEpubJobStore.JobRequest request) throws IOException {
        return locked(sandbox, root -> {
            if (!hasReplacementJournal(root, request)) {
                store.cancelUnprepared(root, request);
                releaseReservation(root, request.operationId());
                return State.ROLLED_BACK;
            }
            return rollbackWithJournal(root, request);
        });
    }

    private State rollbackWithJournal(Path root, NasEpubJobStore.JobRequest request) throws IOException {
        Context context = context(root, request);
        if (context.journal().state() == State.COMMITTED) {
            throw new IOException("Committed synthetic replacement cannot be rolled back");
        }
        if (context.journal().state() == State.ROLLED_BACK) {
            releaseReservation(root, request.operationId());
            return State.ROLLED_BACK;
        }
        requireReservation(root, request.operationId());
        State state = rollbackLocked(root, context);
        releaseReservation(root, request.operationId());
        return state;
    }

    private boolean hasReplacementJournal(Path root, NasEpubJobStore.JobRequest request) throws IOException {
        if (request == null || request.operationId() == null) {
            throw new IOException("Missing synthetic operation ID");
        }
        NasEpubPrototype.validateBatch(request.batch());
        Path jobs = store.storageRoot(root);
        Path job = jobs.resolve(request.operationId().toString());
        Path replacement = job.resolve("replacement");
        for (Path directory : List.of(jobs, job, replacement)) {
            try {
                if (!Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory()) {
                    throw new IOException("Invalid synthetic replacement directory");
                }
            } catch (NoSuchFileException e) {
                return false;
            }
        }
        try {
            Files.readAttributes(replacement.resolve("journal.json"), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    State finish(Path sandbox, NasEpubJobStore.JobRequest request) throws IOException {
        return locked(sandbox, root -> {
            Context context = context(root, request);
            if (context.journal().state() == State.COMMITTED) {
                releaseReservation(root, request.operationId());
                return State.COMMITTED;
            }
            requireReservation(root, request.operationId());
            if (context.journal().state() != State.APPLIED) {
                throw new IOException("Only an applied synthetic replacement can be finalized");
            }
            checkBackups(context);
            verifyPermissions(root, context);
            for (Entry entry : context.entries()) {
                checkFile(root.resolve(entry.input()), entry.outputHash());
            }
            writeState(context, State.COMMITTED);
            releaseReservation(root, request.operationId());
            return State.COMMITTED;
        });
    }

    private State rollbackLocked(Path root, Context context) throws IOException {
        State state = readJournal(context.replacement().resolve("journal.json"), context.entries()).state();
        if (state == State.ROLLED_BACK) {
            checkTargets(root, context.entries(), false);
            return State.ROLLED_BACK;
        }
        checkBackups(context);
        if (state == State.PREPARED) {
            verifyPermissions(root, context);
            checkTargets(root, context.entries(), false);
            writeState(context, State.ROLLED_BACK);
            return State.ROLLED_BACK;
        }

        writeState(context, State.ROLLING_BACK);
        IOException failure = null;
        for (int i = 0; i < context.entries().size(); i++) {
            Entry entry = context.entries().get(i);
            Path target = root.resolve(entry.input());
            try {
                verifyPermission(target, context, i);
                String hash = checkedHash(target);
                if (hash.equalsIgnoreCase(entry.originalHash())) {
                    continue;
                }
                if (!hash.equalsIgnoreCase(entry.outputHash())) {
                    throw new IOException("Synthetic input changed; refusing to overwrite it during rollback");
                }
                copyDurably(context.replacement().resolve(i + ".bak"), target, root, true, snapshot(context, i));
                checkFile(target, entry.originalHash());
                verifyPermission(target, context, i);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
        writeState(context, State.ROLLED_BACK);
        return State.ROLLED_BACK;
    }

    private static Path reservation(Path root) {
        return root.resolve(".grimmory-synthetic-jobs").resolve("replacement-reservation");
    }

    private static void acquireReservation(Path root, UUID operationId) throws IOException {
        Path target = reservation(root);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            requireReservation(root, operationId);
            return;
        }
        Path directory = target.getParent();
        Path temporary = Files.createTempFile(directory, ".reservation-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer content = ByteBuffer.wrap(operationId.toString().getBytes(StandardCharsets.US_ASCII));
                while (content.hasRemaining()) {
                    channel.write(content);
                }
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            NasEpubJobStore.syncDirectory(directory);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    private static void requireReservation(Path root, UUID operationId) throws IOException {
        Path target = reservation(root);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) != 36
                || !Files.readString(target, StandardCharsets.US_ASCII).equals(operationId.toString())) {
            throw new IOException("Synthetic sandbox is reserved by another operation or reservation is invalid");
        }
    }

    private static void releaseReservation(Path root, UUID operationId) throws IOException {
        Path target = reservation(root);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) != 36) {
            throw new IOException("Invalid synthetic reservation");
        }
        if (Files.readString(target, StandardCharsets.US_ASCII).equals(operationId.toString())) {
            Files.delete(target);
            NasEpubJobStore.syncDirectory(target.getParent());
        }
    }

    private Context context(Path root, NasEpubJobStore.JobRequest request) throws IOException {
        Path job = job(root, request);
        Path replacement = job.resolve("replacement");
        if (!Files.isDirectory(replacement, LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(job.resolve("result.json"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synthetic replacement has not been prepared");
        }
        Path requestFile = job.resolve("request.json");
        Path resultFile = job.resolve("result.json");
        if (!Files.isRegularFile(requestFile, LinkOption.NOFOLLOW_LINKS)
                || Files.size(requestFile) > 6L * 1024 * 1024
                || !Arrays.equals(Files.readAllBytes(requestFile), mapper.writeValueAsBytes(request.batch()))
                || Files.size(resultFile) > 64L * 1024) {
            throw new IOException("Invalid synthetic job data");
        }
        List<Entry> entries = entries(request, mapper.readValue(Files.readAllBytes(resultFile),
                NasEpubJobStore.JobResult.class));
        return new Context(job, replacement, entries, readJournal(replacement.resolve("journal.json"), entries));
    }

    private static List<Entry> entries(NasEpubJobStore.JobRequest request, NasEpubJobStore.JobResult result)
            throws IOException {
        if (request == null || request.operationId() == null || result == null
                || !request.operationId().equals(result.operationId()) || result.batch() == null
                || result.batch().results() == null
                || result.batch().results().size() != request.batch().requests().size()) {
            throw new IOException("Invalid synthetic replacement result");
        }
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < result.batch().results().size(); i++) {
            var item = request.batch().requests().get(i);
            var output = result.batch().results().get(i);
            if (item.inputSha256() == null || !item.inputSha256().matches("[a-fA-F0-9]{64}")
                    || output == null || !Objects.equals(item.output(), output.output())
                    || output.outputSha256() == null || !output.outputSha256().matches("[a-f0-9]{64}")) {
                throw new IOException("Invalid synthetic replacement entry");
            }
            entries.add(new Entry(NasEpubPrototype.fileName(item.input()), item.inputSha256(),
                    NasEpubPrototype.fileName(output.output()), output.outputSha256()));
        }
        return List.copyOf(entries);
    }

    private Path job(Path root, NasEpubJobStore.JobRequest request) throws IOException {
        if (request == null || request.operationId() == null) {
            throw new IOException("Missing synthetic operation ID");
        }
        NasEpubPrototype.validateBatch(request.batch());
        Path jobs = store.storageRoot(root);
        Path job = jobs.resolve(request.operationId().toString());
        if (!Files.isDirectory(jobs, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(job, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synthetic job does not exist");
        }
        return job;
    }

    private static void checkBackups(Context context) throws IOException {
        for (int i = 0; i < context.entries().size(); i++) {
            checkFile(context.replacement().resolve(i + ".bak"), context.entries().get(i).originalHash());
        }
    }

    private NasSynologyPermissions.Snapshot snapshot(Context context, int index) {
        return permissions == null ? null : context.journal().permissions().get(index);
    }

    private void verifyPermission(Path path, Context context, int index) throws IOException {
        if (permissions != null) {
            permissions.verify(path, snapshot(context, index));
        }
    }

    private void verifyPermissions(Path root, Context context) throws IOException {
        for (int i = 0; i < context.entries().size(); i++) {
            verifyPermission(root.resolve(context.entries().get(i).input()), context, i);
        }
    }

    private static void checkTargets(Path root, List<Entry> entries, boolean allowOutputs) throws IOException {
        for (Entry entry : entries) {
            String hash = checkedHash(root.resolve(entry.input()));
            if (!hash.equalsIgnoreCase(entry.originalHash())
                    && !(allowOutputs && hash.equalsIgnoreCase(entry.outputHash()))) {
                throw new IOException("Synthetic input changed unexpectedly");
            }
        }
    }

    private static void checkFile(Path file, String hash) throws IOException {
        if (!checkedHash(file).equalsIgnoreCase(hash)) {
            throw new IOException("Synthetic file is missing or modified");
        }
    }

    private static String checkedHash(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_EPUB_BYTES) {
            throw new IOException("Invalid synthetic file");
        }
        return NasEpubPrototype.sha256(file);
    }

    private Journal readJournal(Path file, List<Entry> expected) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 4L * 1024 * 1024) {
            throw new IOException("Invalid synthetic replacement journal");
        }
        Journal journal = mapper.readValue(Files.readAllBytes(file), Journal.class);
        if (journal == null || journal.state() == null || !expected.equals(journal.entries())
                || journal.permissions() == null
                || journal.permissions().size() != (permissions == null ? 0 : expected.size())) {
            throw new IOException("Synthetic replacement journal does not match the job");
        }
        for (var item : journal.permissions()) {
            NasSynologyPermissions.validate(item);
        }
        return journal;
    }

    private void writeState(Context context, State state) throws IOException {
        writeJournal(context.replacement(), new Journal(state, context.entries(), context.journal().permissions()));
    }

    private void writeJournal(Path directory, Journal journal) throws IOException {
        Path temporary = Files.createTempFile(directory, ".journal-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(mapper.writeValueAsBytes(journal));
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, directory.resolve("journal.json"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            NasEpubJobStore.syncDirectory(directory);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    private void copyDurably(Path source, Path target, Path directory, boolean replace,
                             NasSynologyPermissions.Snapshot snapshot) throws IOException {
        Path temporary = Files.createTempFile(directory, ".replacement-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (snapshot != null) {
                permissions.copy(target, temporary, snapshot);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (snapshot != null) {
                permissions.verify(target, snapshot);
            }
            if (replace) {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // The caller holds the synthetic sandbox lock.
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new FileAlreadyExistsException(target.toString());
                }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            }
            NasEpubJobStore.syncDirectory(directory);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    private static Path directory(Path path) throws IOException {
        try {
            Files.createDirectory(path);
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid synthetic replacement directory", e);
            }
        }
        return path;
    }

    private static <T> T locked(Path sandbox, Operation<T> operation) throws IOException {
        Path root = sandbox.toRealPath();
        LOCK.lock();
        try (FileChannel channel = FileChannel.open(root.resolve(".grimmory-synthetic-replacement.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = channel.lock()) {
            return operation.run(root);
        } finally {
            LOCK.unlock();
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run(Path root) throws IOException;
    }

    private record Entry(String input, String originalHash, String output, String outputHash) {}
    private record Journal(State state, List<Entry> entries, List<NasSynologyPermissions.Snapshot> permissions) {}
    private record Context(Path job, Path replacement, List<Entry> entries, Journal journal) {}
}
