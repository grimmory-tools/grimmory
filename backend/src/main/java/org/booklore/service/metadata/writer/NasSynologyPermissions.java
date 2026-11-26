package org.booklore.service.metadata.writer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class NasSynologyPermissions {
    private static final Path TOOL = Path.of("/opt/synology/synoacltool");
    private static final String LIBRARIES = "/opt/synology/lib";
    private static final int MAX_OUTPUT = 64 * 1024;
    private final Commands commands;
    private final Ownership ownership;

    public record Identity(int uid, int gid, int mode) {}
    public record Snapshot(Identity identity, String archive, String entries) {}

    public interface Commands {
        String run(String action, Path... paths) throws IOException;

        default Snapshot capture(Path path, Identity identity) throws IOException {
            List<String> archiveLines = run("-get", path).lines()
                    .filter(line -> line.startsWith("Archive:")).map(String::stripTrailing).toList();
            String entries = run("-getace", path).stripTrailing();
            return new Snapshot(identity, archiveLines.size() == 1 ? archiveLines.getFirst() : "", entries);
        }

        default void copy(Path source, Path target, Snapshot expected) throws IOException {
            run("-copy", source, target);
        }
    }

    interface Ownership {
        Identity read(Path path) throws IOException;
        void set(Path path, Identity identity) throws IOException;
    }

    NasSynologyPermissions() throws IOException {
        this(NasSynologyPermissions::runTool, new UnixOwnership());
        if (!Files.isRegularFile(TOOL, LinkOption.NOFOLLOW_LINKS) || !Files.isExecutable(TOOL)
                || !Files.isDirectory(Path.of(LIBRARIES), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synology ACL tool or libraries are unavailable");
        }
    }

    NasSynologyPermissions(Commands commands) {
        this(commands, new UnixOwnership());
    }

    NasSynologyPermissions(Commands commands, Ownership ownership) {
        this.commands = Objects.requireNonNull(commands);
        this.ownership = Objects.requireNonNull(ownership);
    }

    public Snapshot capture(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("ACL target must be a regular file");
        }
        Identity identity = ownership.read(path);
        Snapshot snapshot = commands.capture(path, identity);
        if (snapshot == null || snapshot.archive() == null || !snapshot.archive().startsWith("Archive:")
                || !snapshot.archive().contains("is_support_ACL")
                || !(snapshot.archive().contains("is_inherit") || snapshot.archive().contains("has_ACL"))
                || snapshot.archive().length() > 512 || snapshot.entries() == null || snapshot.entries().isBlank()
                || snapshot.entries().length() > 4096
                || !identity.equals(ownership.read(path))) {
            throw new IOException("Unsupported or changed Synology ACL");
        }
        validate(snapshot);
        return snapshot;
    }

    public void verify(Path path, Snapshot expected) throws IOException {
        validate(expected);
        if (!expected.equals(capture(path))) {
            throw new IOException("Synology ownership, mode or ACL changed");
        }
    }

    public static void validate(Snapshot snapshot) throws IOException {
        if (snapshot == null || snapshot.identity() == null || snapshot.identity().uid() < 0
                || snapshot.identity().gid() < 0 || snapshot.identity().mode() < 0
                || snapshot.identity().mode() > 07777 || snapshot.archive() == null
                || snapshot.archive().length() > 512 || !snapshot.archive().startsWith("Archive:")
                || !snapshot.archive().contains("is_support_ACL")
                || !(snapshot.archive().contains("is_inherit") || snapshot.archive().contains("has_ACL"))
                || snapshot.entries() == null || snapshot.entries().isBlank() || snapshot.entries().length() > 4096) {
            throw new IOException("Invalid Synology ACL snapshot");
        }
    }

    public void copy(Path source, Path target, Snapshot expected) throws IOException {
        verify(source, expected);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("ACL candidate must be a regular file");
        }
        commands.copy(source, target, expected);
        ownership.set(target, expected.identity());
        verify(target, expected);
        verify(source, expected);
    }

    private static String runTool(String action, Path... paths) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add(TOOL.toString());
        command.add(action);
        for (Path path : paths) {
            command.add(path.toString());
        }
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("LD_LIBRARY_PATH", LIBRARIES);
        Process process = builder.start();
        FutureTask<byte[]> output = new FutureTask<>(() -> {
            try (var stream = process.getInputStream()) {
                return stream.readNBytes(MAX_OUTPUT + 1);
            }
        });
        Thread.ofVirtual().start(output);
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                throw new IOException("Synology ACL tool timed out");
            }
            byte[] bytes = output.get(2, TimeUnit.SECONDS);
            if (bytes.length > MAX_OUTPUT || process.exitValue() != 0) {
                throw new IOException("Synology ACL tool failed or returned too much output");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Synology ACL tool interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Cannot read Synology ACL tool output", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static final class UnixOwnership implements Ownership {
        @Override
        public Identity read(Path path) throws IOException {
            try {
                var attributes = Files.readAttributes(path, "unix:uid,gid,mode", LinkOption.NOFOLLOW_LINKS);
                return new Identity((Integer) attributes.get("uid"), (Integer) attributes.get("gid"),
                        (Integer) attributes.get("mode") & 07777);
            } catch (UnsupportedOperationException e) {
                throw new IOException("Unix file attributes are unavailable", e);
            }
        }

        @Override
        public void set(Path path, Identity identity) throws IOException {
            try {
                Identity current = read(path);
                if (current.gid() != identity.gid()) {
                    Files.setAttribute(path, "unix:gid", identity.gid(), LinkOption.NOFOLLOW_LINKS);
                }
                if (current.uid() != identity.uid()) {
                    Files.setAttribute(path, "unix:uid", identity.uid(), LinkOption.NOFOLLOW_LINKS);
                }
                if (current.mode() != identity.mode()) {
                    Files.setAttribute(path, "unix:mode", identity.mode(), LinkOption.NOFOLLOW_LINKS);
                }
            } catch (UnsupportedOperationException e) {
                throw new IOException("Unix file ownership is unavailable", e);
            }
        }
    }
}
