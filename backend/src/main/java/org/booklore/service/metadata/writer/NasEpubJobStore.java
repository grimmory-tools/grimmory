package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Slf4j
public final class NasEpubJobStore {
    private static final long MAX_INPUT_BYTES = 64L * 1024 * 1024;
    private static final ReentrantLock[] LOCKS = IntStream.range(0, 64)
            .mapToObj(index -> new ReentrantLock()).toArray(ReentrantLock[]::new);
    private static final ReentrantLock LOCATION_LOCK = new ReentrantLock();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path stateDirectory;

    public NasEpubJobStore() {
        this(null);
    }

    NasEpubJobStore(Path stateDirectory) {
        this.stateDirectory = stateDirectory;
    }

    public record JobRequest(UUID operationId, NasEpubPrototype.BatchRequest batch) {}
    public record JobResult(UUID operationId, NasEpubPrototype.BatchResult batch) {}

    void run(InputStream input, OutputStream output, Path sandbox) throws IOException {
        JobRequest request = mapper.readValue(NasEpubPrototype.readRequest(input), JobRequest.class);
        output.write(mapper.writeValueAsBytes(execute(sandbox, request)));
        output.write('\n');
    }

    JobResult execute(Path sandbox, JobRequest request) throws IOException {
        Path root = sandbox.toRealPath();
        Path job = ensureJob(root, request);
        return locked(job, () -> executeLocked(root, job, request));
    }

    void cancelUnprepared(Path sandbox, JobRequest request) throws IOException {
        Path root = sandbox.toRealPath();
        Path job = ensureJob(root, request);
        locked(job, () -> {
            ensureRequest(job, request);
            Path marker = job.resolve("cancelled");
            byte[] content = "synthetic-cancelled-v1".getBytes(StandardCharsets.US_ASCII);
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(marker) != content.length
                        || !Arrays.equals(content, Files.readAllBytes(marker))) {
                    throw new IOException("Invalid synthetic cancellation marker");
                }
            } else {
                durableWrite(job, marker, content);
            }
            return null;
        });
    }

    private Path ensureJob(Path root, JobRequest request) throws IOException {
        if (request == null || request.operationId() == null) {
            throw new IllegalArgumentException("An operation ID is required");
        }
        NasEpubPrototype.validateBatch(request.batch());
        Path jobs = storageRoot(root);
        Path job = ensureDirectory(jobs.resolve(request.operationId().toString()));
        syncDirectory(jobs);
        return job;
    }

    Path storageRoot(Path root) throws IOException {
        Path control = ensureDirectory(root.resolve(".grimmory-synthetic-jobs"));
        syncDirectory(root);
        if (stateDirectory != null && (!stateDirectory.isAbsolute()
                || !Files.isDirectory(stateDirectory, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Synthetic state directory must be an existing absolute directory");
        }
        Path storage = stateDirectory == null ? control : stateDirectory.toRealPath();
        if (!Files.isDirectory(storage, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synthetic state directory is not a real directory");
        }
        if (stateDirectory != null && (storage.startsWith(root) || root.startsWith(storage))) {
            throw new IOException("Synthetic state directory must be separate from the sandbox");
        }

        LOCATION_LOCK.lock();
        try (FileChannel controlChannel = FileChannel.open(control.resolve(".location.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = controlChannel.lock()) {
            Path location = control.resolve("storage-location");
            byte[] expected = storage.toString().getBytes(StandardCharsets.UTF_8);
            boolean alreadyBound = Files.exists(location, LinkOption.NOFOLLOW_LINKS);
            if (alreadyBound) {
                verifyMarker(location, expected, "Synthetic state location changed");
            } else {
                if (Files.exists(control.resolve("sandbox-identity"), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Synthetic state location marker is missing");
                }
                if (stateDirectory != null) {
                    try (var entries = Files.list(control)) {
                        if (entries.anyMatch(path -> !path.getFileName().toString().equals(".location.lock"))) {
                            throw new IOException("Existing synthetic jobs cannot be moved to another state directory");
                        }
                    }
                }
            }
            if (stateDirectory != null) {
                bindSandbox(storage, root, control, !alreadyBound);
            }
            if (!alreadyBound) {
                durableWrite(control, location, expected);
            }
            return storage;
        } finally {
            LOCATION_LOCK.unlock();
        }
    }

    private static void bindSandbox(Path storage, Path root, Path control, boolean allowCreate) throws IOException {
        try (FileChannel channel = FileChannel.open(storage.resolve(".sandbox.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = channel.lock()) {
            BasicFileAttributes attributes = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()) {
                throw new IOException("Synthetic sandbox identity is unavailable");
            }
            Path identityFile = control.resolve("sandbox-identity");
            if (!Files.exists(identityFile, LinkOption.NOFOLLOW_LINKS)) {
                if (!allowCreate) {
                    throw new IOException("Synthetic sandbox identity is missing");
                }
                durableWrite(control, identityFile, UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII));
            }
            if (!Files.isRegularFile(identityFile, LinkOption.NOFOLLOW_LINKS) || Files.size(identityFile) != 36) {
                throw new IOException("Invalid synthetic sandbox identity");
            }
            String sandboxId = Files.readString(identityFile, StandardCharsets.US_ASCII);
            try {
                if (!UUID.fromString(sandboxId).toString().equals(sandboxId)) {
                    throw new IOException("Invalid synthetic sandbox identity");
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid synthetic sandbox identity", e);
            }
            byte[] identity = (root + "\n" + sandboxId + "\n" + attributes.fileKey()).getBytes(StandardCharsets.UTF_8);
            Path marker = storage.resolve("sandbox-binding");
            if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
                verifyMarker(marker, identity, "Synthetic state belongs to another sandbox");
            } else {
                if (!allowCreate) {
                    throw new IOException("Synthetic state sandbox binding is missing");
                }
                try (var entries = Files.list(storage)) {
                    if (entries.anyMatch(path -> !path.getFileName().toString().equals(".sandbox.lock"))) {
                        throw new IOException("Synthetic state directory is not empty");
                    }
                }
                durableWrite(storage, marker, identity);
            }
        }
    }

    private static void verifyMarker(Path marker, byte[] expected, String message) throws IOException {
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || Files.size(marker) != expected.length
                || !Arrays.equals(Files.readAllBytes(marker), expected)) {
            throw new IOException(message);
        }
    }

    private static <T> T locked(Path job, JobOperation<T> operation) throws IOException {
        ReentrantLock localLock = LOCKS[Math.floorMod(job.hashCode(), LOCKS.length)];
        localLock.lock();
        try (FileChannel lockChannel = FileChannel.open(job.resolve("operation.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
             FileLock ignored = lockChannel.lock()) {
            return operation.run();
        } finally {
            localLock.unlock();
        }
    }

    private JobResult executeLocked(Path root, Path job, JobRequest request) throws IOException {
        ensureRequest(job, request);
        if (Files.exists(job.resolve("cancelled"), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synthetic operation was cancelled");
        }
        Path resultFile = job.resolve("result.json");
        if (Files.exists(resultFile, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(resultFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(resultFile) > 64L * 1024) {
                throw new IOException("Invalid operation result file");
            }
            JobResult result = mapper.readValue(Files.readAllBytes(resultFile), JobResult.class);
            verifyResult(job, request, result);
            return result;
        }
        for (NasEpubPrototype.Request item : request.batch().requests()) {
            Path source = root.resolve(NasEpubPrototype.fileName(item.input()));
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.size(source) > MAX_INPUT_BYTES) {
                throw new IOException("Invalid synthetic input file");
            }
            Files.deleteIfExists(job.resolve(NasEpubPrototype.fileName(item.output())));
            Files.copy(source, job.resolve(item.input()), StandardCopyOption.REPLACE_EXISTING);
        }
        NasEpubPrototype.BatchResult batch = NasEpubPrototype.processBatch(job, request.batch());
        for (NasEpubPrototype.Result result : batch.results()) {
            try (FileChannel output = FileChannel.open(job.resolve(result.output()), StandardOpenOption.WRITE)) {
                output.force(true);
            }
        }
        syncDirectory(job);
        JobResult result = new JobResult(request.operationId(), batch);
        durableWrite(job, resultFile, mapper.writeValueAsBytes(result));
        return result;
    }

    private void ensureRequest(Path job, JobRequest request) throws IOException {
        byte[] content = mapper.writeValueAsBytes(request.batch());
        Path requestFile = job.resolve("request.json");
        if (Files.exists(requestFile, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(requestFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(requestFile) > 6L * 1024 * 1024
                    || !Arrays.equals(content, Files.readAllBytes(requestFile))) {
                throw new IOException("Operation ID already belongs to a different request");
            }
        } else {
            durableWrite(job, requestFile, content);
        }
    }

    @FunctionalInterface
    private interface JobOperation<T> {
        T run() throws IOException;
    }

    private static void verifyResult(Path job, JobRequest request, JobResult result) throws IOException {
        if (result == null || !request.operationId().equals(result.operationId()) || result.batch() == null
                || result.batch().results() == null
                || result.batch().results().size() != request.batch().requests().size()) {
            throw new IOException("Invalid persisted operation result");
        }
        for (int i = 0; i < result.batch().results().size(); i++) {
            NasEpubPrototype.Result item = result.batch().results().get(i);
            NasEpubPrototype.Request expected = request.batch().requests().get(i);
            if (item == null || !Objects.equals(expected.output(), item.output())
                    || item.outputSha256() == null || !item.outputSha256().matches("[a-f0-9]{64}")) {
                throw new IOException("Invalid persisted operation output");
            }
            Path output = job.resolve(item.output());
            if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)
                    || !item.outputSha256().equals(NasEpubPrototype.sha256(output))) {
                throw new IOException("Persisted operation output is missing or modified");
            }
        }
    }

    private static Path ensureDirectory(Path path) throws IOException {
        try {
            Files.createDirectory(path);
        } catch (FileAlreadyExistsException e) {
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Operation directory is not a real directory", e);
            }
        }
        return path;
    }

    private static void durableWrite(Path directory, Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(directory, ".journal-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            syncDirectory(directory);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    static void syncDirectory(Path directory) throws IOException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            log.warn("Directory sync is unavailable on Windows; power-loss durability is not established");
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}
