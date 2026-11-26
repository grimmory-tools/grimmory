import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class NfsAclCapabilityProbe {
    private record Snapshot(UserPrincipal owner, GroupPrincipal group, Set<PosixFilePermission> permissions,
                            List<java.nio.file.attribute.AclEntry> acl) {}

    private NfsAclCapabilityProbe() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: NfsAclCapabilityProbe <isolated-test-directory>");
        }
        Path root = Path.of(args[0]).toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Test path is not a directory");
        }
        Path probe = Files.createTempDirectory(root, ".grimmory-nfs-acl-probe-" + UUID.randomUUID());
        Path source = probe.resolve("source.synthetic");
        Path candidate = probe.resolve("candidate.synthetic");
        try {
            Files.writeString(source, "original synthetic content", StandardCharsets.UTF_8);
            Files.writeString(candidate, "candidate synthetic content", StandardCharsets.UTF_8);
            Snapshot expected = capture(source);
            applyAndVerify(expected, candidate);
            Files.move(candidate, source, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.readString(source, StandardCharsets.UTF_8).equals("candidate synthetic content")) {
                throw new IOException("Atomic replacement produced unexpected content");
            }
            if (!expected.equals(capture(source))) {
                throw new IOException("Owner, POSIX permissions, or ACL changed after atomic replacement");
            }
            System.out.println("PASS: Java ACL view, ACL copy, owner/mode check and atomic replacement");
        } finally {
            Files.deleteIfExists(candidate);
            Files.deleteIfExists(source);
            Files.deleteIfExists(probe);
        }
    }

    private static Snapshot capture(Path file) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (aclView == null) {
            throw new IOException("FAIL_CLOSED: Java does not expose an ACL view for this NFS mount");
        }
        PosixFileAttributeView posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (posixView == null) {
            return new Snapshot(Files.getOwner(file, LinkOption.NOFOLLOW_LINKS), null, null,
                    List.copyOf(aclView.getAcl()));
        }
        PosixFileAttributes posix = posixView.readAttributes();
        return new Snapshot(posix.owner(), posix.group(), Set.copyOf(posix.permissions()),
                List.copyOf(aclView.getAcl()));
    }

    private static void applyAndVerify(Snapshot expected, Path file) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (aclView == null) {
            throw new IOException("FAIL_CLOSED: Java does not expose an ACL view for the candidate");
        }
        PosixFileAttributeView posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if ((expected.permissions() == null) != (posixView == null)) {
            throw new IOException("POSIX attribute support differs between source and candidate");
        }
        if (!Objects.equals(Files.getOwner(file, LinkOption.NOFOLLOW_LINKS), expected.owner())) {
            Files.setOwner(file, expected.owner());
        }
        if (posixView != null) {
            PosixFileAttributes current = posixView.readAttributes();
            if (!current.group().equals(expected.group())) {
                posixView.setGroup(expected.group());
            }
        }
        aclView.setAcl(expected.acl());
        if (posixView != null) {
            posixView.setPermissions(expected.permissions());
        }
        if (!expected.equals(capture(file))) {
            throw new IOException("Could not preserve owner, POSIX permissions, and ACL");
        }
    }
}
