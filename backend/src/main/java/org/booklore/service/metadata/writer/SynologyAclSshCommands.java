package org.booklore.service.metadata.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.booklore.config.AppProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class SynologyAclSshCommands implements NasSynologyPermissions.Commands {
    private static final Path SSH = Path.of("/usr/bin/ssh");
    private static final int MAX_RESPONSE_BYTES = 32 * 1024;
    private static final int MAX_ERROR_BYTES = 4 * 1024;

    private final AppProperties.SynologyAcl settings;
    private final ObjectMapper objectMapper;

    public SynologyAclSshCommands(AppProperties properties, ObjectMapper objectMapper) {
        this.settings = Objects.requireNonNull(properties.getSynologyAcl());
        this.objectMapper = Objects.requireNonNull(objectMapper);
        if (settings.isEnabled()) {
            validateSettings();
        }
    }

    @Override
    public String run(String action, Path... paths) throws IOException {
        throw new IOException("Direct Synology ACL commands are disabled; use a validated operation");
    }

    @Override
    public NasSynologyPermissions.Snapshot capture(Path path, NasSynologyPermissions.Identity identity)
            throws IOException {
        requireEnabled();
        Path localPath = requireLibraryPath(path);
        HelperSnapshot snapshot = request(Map.of("action", "snapshot", "path", relative(localPath)));
        requireIdentity(snapshot, identity);
        return new NasSynologyPermissions.Snapshot(identity, snapshot.archive(), snapshot.entries());
    }

    @Override
    public void copy(Path source, Path target, NasSynologyPermissions.Snapshot expected) throws IOException {
        requireEnabled();
        Path localSource = requireLibraryPath(source);
        Path localTarget = requireLibraryPath(target);
        if (!localSource.getParent().equals(localTarget.getParent())) {
            throw new IOException("Synology ACL copies must target a sibling file");
        }
        NasSynologyPermissions.Identity identity = expected.identity();
        HelperSnapshot actual = request(Map.of(
                "action", "copy-verify",
                "source", relative(localSource),
                "target", relative(localTarget),
                "expected", Map.of(
                        "uid", identity.uid(),
                        "gid", identity.gid(),
                        "mode", identity.mode(),
                        "archive", expected.archive(),
                        "entries", expected.entries())));
        requireIdentity(actual, identity);
        if (!expected.archive().equals(actual.archive()) || !expected.entries().equals(actual.entries())) {
            throw new IOException("Synology ACL helper did not preserve the expected ACL");
        }
    }

    private HelperSnapshot request(Map<String, ?> request) throws IOException {
        byte[] input = objectMapper.writeValueAsBytes(request);
        if (input.length > MAX_RESPONSE_BYTES) {
            throw new IOException("Synology ACL request is too large");
        }
        ProcessBuilder builder = new ProcessBuilder(command());
        builder.environment().clear();
        builder.environment().put("PATH", "/usr/bin:/bin");
        builder.environment().put("LANG", "C");
        Process process = builder.start();
        FutureTask<byte[]> stdout = readBounded(process.getInputStream(), MAX_RESPONSE_BYTES);
        FutureTask<byte[]> stderr = readBounded(process.getErrorStream(), MAX_ERROR_BYTES);
        Thread.ofVirtual().start(stdout);
        Thread.ofVirtual().start(stderr);
        try {
            try (OutputStream output = process.getOutputStream()) {
                output.write(input);
                output.write('\n');
            }
            if (!process.waitFor(settings.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                throw new IOException("Synology ACL helper timed out");
            }
            byte[] response = stdout.get(2, TimeUnit.SECONDS);
            byte[] errors = stderr.get(2, TimeUnit.SECONDS);
            if (response.length > MAX_RESPONSE_BYTES || errors.length > MAX_ERROR_BYTES
                    || process.exitValue() != 0) {
                throw new IOException("Synology ACL helper failed");
            }
            return objectMapper.readValue(response, HelperSnapshot.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Synology ACL helper was interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Cannot read Synology ACL helper response", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private List<String> command() throws IOException {
        List<String> command = new ArrayList<>();
        command.add(SSH.toString());
        command.addAll(List.of(
                "-F", "/dev/null",
                "-p", Integer.toString(settings.getPort()),
                "-i", settings.getIdentityFile(),
                "-o", "BatchMode=yes",
                "-o", "IdentitiesOnly=yes",
                "-o", "PubkeyAuthentication=yes",
                "-o", "PreferredAuthentications=publickey",
                "-o", "GSSAPIAuthentication=no",
                "-o", "HostbasedAuthentication=no",
                "-o", "StrictHostKeyChecking=yes",
                "-o", "UpdateHostKeys=no",
                "-o", "UserKnownHostsFile=" + settings.getKnownHostsFile(),
                "-o", "GlobalKnownHostsFile=/dev/null",
                "-o", "PasswordAuthentication=no",
                "-o", "KbdInteractiveAuthentication=no",
                "-o", "ClearAllForwardings=yes",
                "-o", "ForwardAgent=no",
                "-o", "ForwardX11=no",
                "-o", "ProxyCommand=none",
                "-o", "ProxyJump=none",
                "-o", "ControlMaster=no",
                "-o", "ControlPath=none",
                "-o", "PermitLocalCommand=no",
                "-o", "RequestTTY=no",
                "-o", "ConnectTimeout=5",
                "-T",
                settings.getUser() + "@" + settings.getHost()));
        return command;
    }

    private Path requireLibraryPath(Path path) throws IOException {
        if (path == null || !path.isAbsolute() || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Synology ACL target must be an absolute regular file");
        }
        Path root = Path.of(settings.getLocalRoot()).normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IOException("Synology ACL path is outside the configured library root");
        }
        return normalized;
    }

    private String relative(Path path) throws IOException {
        Path root = Path.of(settings.getLocalRoot()).normalize();
        String relative = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("../")
                || relative.equals("..") || relative.contains("\n") || relative.contains("\r")) {
            throw new IOException("Invalid relative Synology ACL path");
        }
        return relative;
    }

    private void requireEnabled() throws IOException {
        if (!settings.isEnabled()) {
            throw new IOException("Synology ACL helper is not configured");
        }
    }

    private void validateSettings() {
        if (settings.getHost() == null || !settings.getHost().matches("[A-Za-z0-9._:-]{1,253}")
                || settings.getUser() == null || !settings.getUser().matches("[A-Za-z_][A-Za-z0-9_-]{0,31}")
                || settings.getPort() < 1 || settings.getPort() > 65535
                || settings.getTimeoutSeconds() < 1 || settings.getTimeoutSeconds() > 60
                || settings.getIdentityFile() == null || settings.getKnownHostsFile() == null
                || settings.getLocalRoot() == null) {
            throw new IllegalStateException("Invalid Synology ACL helper configuration");
        }
        Path localRoot = Path.of(settings.getLocalRoot());
        if (!localRoot.isAbsolute() || !localRoot.normalize().equals(localRoot)
                || !Files.isDirectory(localRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(localRoot)) {
            throw new IllegalStateException("Synology ACL local root must be an existing canonical directory");
        }
        Path identity = requireSecureFile(settings.getIdentityFile(), true);
        requireSecureFile(settings.getKnownHostsFile(), false);
        if (!Files.isExecutable(SSH) || !identity.isAbsolute()) {
            throw new IllegalStateException("SSH client or identity file is unavailable");
        }
    }

    private Path requireSecureFile(String value, boolean privateKey) {
        Path path = Path.of(value);
        if (!path.isAbsolute() || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("Synology ACL SSH files must be absolute regular files");
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            Set<PosixFilePermission> forbidden = EnumSet.of(
                    PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE);
            if (privateKey) {
                forbidden.addAll(EnumSet.of(PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
            }
            if (permissions.stream().anyMatch(forbidden::contains)) {
                throw new IllegalStateException("Synology ACL SSH file permissions are too broad");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot inspect Synology ACL SSH file permissions", e);
        }
        return path;
    }

    private static FutureTask<byte[]> readBounded(java.io.InputStream input, int limit) {
        return new FutureTask<>(() -> {
            try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > limit) {
                        return new byte[limit + 1];
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        });
    }

    private static void requireIdentity(HelperSnapshot snapshot, NasSynologyPermissions.Identity identity)
            throws IOException {
        if (snapshot == null || snapshot.uid() != identity.uid() || snapshot.gid() != identity.gid()
                || snapshot.mode() != identity.mode() || snapshot.archive() == null
                || snapshot.entries() == null) {
            throw new IOException("Synology ACL helper returned mismatched file attributes");
        }
    }

    private record HelperSnapshot(int uid, int gid, int mode, String archive, String entries) {}
}
