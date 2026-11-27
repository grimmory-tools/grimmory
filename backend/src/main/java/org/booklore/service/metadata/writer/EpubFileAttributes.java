package org.booklore.service.metadata.writer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class EpubFileAttributes {
    public record Snapshot(UserPrincipal owner, GroupPrincipal group, Set<PosixFilePermission> permissions,
                           List<AclEntry> acl, NasSynologyPermissions.Snapshot synologyAcl) {}

    private EpubFileAttributes() {
    }

    public static Snapshot capture(Path file) throws IOException {
        return capture(file, null);
    }

    public static Snapshot capture(Path file, NasSynologyPermissions synologyPermissions) throws IOException {
        requireRegularFile(file);
        AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributeView posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (aclView == null) {
            if (synologyPermissions == null || posixView == null) {
                throw new IOException("The file system does not expose ACLs; refusing EPUB replacement");
            }
            PosixFileAttributes posix = posixView.readAttributes();
            NasSynologyPermissions.Snapshot synologyAcl = synologyPermissions.capture(file);
            verifyUnixIdentity(file, posix, synologyAcl.identity());
            return new Snapshot(posix.owner(), posix.group(), Set.copyOf(posix.permissions()), null, synologyAcl);
        }
        if (posixView == null) {
            return new Snapshot(Files.getOwner(file, LinkOption.NOFOLLOW_LINKS), null, null,
                    List.copyOf(aclView.getAcl()), null);
        }
        PosixFileAttributes posix = posixView.readAttributes();
        return new Snapshot(posix.owner(), posix.group(), Set.copyOf(posix.permissions()),
                List.copyOf(aclView.getAcl()), null);
    }

    public static void applyAndVerify(Snapshot expected, Path file) throws IOException {
        applyAndVerify(expected, null, file, null);
    }

    public static void applyAndVerify(Snapshot expected, Path reference, Path file,
                                      NasSynologyPermissions synologyPermissions) throws IOException {
        Objects.requireNonNull(expected);
        requireRegularFile(file);
        AclFileAttributeView aclView = Files.getFileAttributeView(file, AclFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributeView posixView = Files.getFileAttributeView(file, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if ((expected.permissions() == null) != (posixView == null)) {
            throw new IOException("POSIX attributes cannot be preserved for EPUB replacement");
        }
        if (expected.synologyAcl() != null) {
            if (aclView != null || synologyPermissions == null || reference == null) {
                throw new IOException("Synology ACL helper is required to preserve this file");
            }
            synologyPermissions.copy(reference, file, expected.synologyAcl());
            verify(expected, file, synologyPermissions);
            return;
        }
        if (aclView == null || expected.acl() == null) {
            throw new IOException("The file system does not expose ACLs; refusing EPUB replacement");
        }
        if (!Files.getOwner(file, LinkOption.NOFOLLOW_LINKS).equals(expected.owner())) {
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
        verify(expected, file, synologyPermissions);
    }

    public static void verify(Snapshot expected, Path file, NasSynologyPermissions synologyPermissions)
            throws IOException {
        if (expected.synologyAcl() != null) {
            if (synologyPermissions == null) {
                throw new IOException("Synology ACL helper is required to verify this file");
            }
            // The DSM helper is authoritative for Synology ACLs. Local NFS UID/GID
            // attributes can remain cached after the helper updates a sibling inode.
            synologyPermissions.verifyRemote(file, expected.synologyAcl());
            return;
        }
        Snapshot actual = capture(file, synologyPermissions);
        if (!Objects.equals(expected.acl(), actual.acl()) || !expected.owner().equals(actual.owner())
                || !Objects.equals(expected.group(), actual.group())
                || !Objects.equals(expected.permissions(), actual.permissions())
                || !Objects.equals(expected.synologyAcl(), actual.synologyAcl())) {
            throw new IOException("EPUB owner, permissions, or ACL changed during file preparation");
        }
    }

    private static void verifyUnixIdentity(Path file, PosixFileAttributes posix,
                                           NasSynologyPermissions.Identity synology) throws IOException {
        try {
            var unix = Files.readAttributes(file, "unix:uid,gid,mode", LinkOption.NOFOLLOW_LINKS);
            int rawMode = (Integer) unix.get("mode");
            int permissions = rawMode & 0777;
            int expectedPermissions = posix.permissions().stream().mapToInt(permission -> switch (permission) {
                case OWNER_READ -> 0400;
                case OWNER_WRITE -> 0200;
                case OWNER_EXECUTE -> 0100;
                case GROUP_READ -> 0040;
                case GROUP_WRITE -> 0020;
                case GROUP_EXECUTE -> 0010;
                case OTHERS_READ -> 0004;
                case OTHERS_WRITE -> 0002;
                case OTHERS_EXECUTE -> 0001;
            }).sum();
            if (!Objects.equals(unix.get("uid"), synology.uid()) || !Objects.equals(unix.get("gid"), synology.gid())
                    || permissions != expectedPermissions || (rawMode & 07777) != synology.mode()) {
                throw new IOException("NFS and Synology views of file ownership or mode differ");
            }
        } catch (UnsupportedOperationException e) {
            throw new IOException("Unix file identity is unavailable", e);
        }
    }

    private static void requireRegularFile(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("EPUB attribute target must be a regular file");
        }
    }
}
