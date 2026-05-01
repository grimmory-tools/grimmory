package org.booklore.service;

import com.github.gotson.nightcompress.Archive;
import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import org.grimmory.epub4j.native_parsing.NativeArchive;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.nativelib.NativeLibraries;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
public class ArchiveService {
    private static final int LOCK_STRIPE_COUNT = 256;
    private final ReentrantLock[] lockStripes = IntStream.range(0, LOCK_STRIPE_COUNT)
            .mapToObj(_ -> new ReentrantLock())
            .toArray(ReentrantLock[]::new);

    // Route through the JVM-wide serialized native loader.
    private final boolean available = NativeLibraries.get().isLibArchiveAvailable();

    private ReentrantLock getFileLock(Path path) {
        int hash = path.toAbsolutePath().normalize().toString().hashCode();
        return lockStripes[Math.floorMod(hash, LOCK_STRIPE_COUNT)];
    }

    private void requireAvailable() throws IOException {
        if (!available) {
            throw new IOException("LibArchive is not available – cannot process archive");
        }
    }

    public static boolean isAvailable() {
        return NativeLibraries.get().isLibArchiveAvailable();
    }

    public record Entry(String name, long size) {}

    private Entry getEntryFromArchiveEntry(ArchiveEntry archiveEntry) {
        return new Entry(archiveEntry.getName(), archiveEntry.getSize());
    }

    public List<Entry> getEntries(Path path) throws IOException {
        return streamEntries(path).toList();
    }

    public Stream<Entry> streamEntries(Path path) throws IOException {
        requireAvailable();
        ReentrantLock lock = getFileLock(path);
        lock.lock();
        try {
            List<ArchiveEntry> entries = Archive.getEntries(path);
            return entries.stream().map(this::getEntryFromArchiveEntry);
        } catch (LibArchiveException e) {
            throw new IOException("Failed to read archive", e);
        } finally {
            lock.unlock();
        }
    }

    public List<String> getEntryNames(Path path) throws IOException {
        return streamEntryNames(path).toList();
    }

    public Stream<String> streamEntryNames(Path path) throws IOException {
        requireAvailable();
        ReentrantLock lock = getFileLock(path);
        lock.lock();
        try {
            List<ArchiveEntry> entries = Archive.getEntries(path);
            return entries.stream().map(ArchiveEntry::getName);
        } catch (LibArchiveException e) {
            throw new IOException("Failed to read archive", e);
        } finally {
            lock.unlock();
        }
    }

    public long transferEntryTo(Path path, String entryName, OutputStream outputStream) throws IOException {
        ReentrantLock lock = getFileLock(path);
        lock.lock();
        try {
            // 1. Try NativeArchive (Panama) first.
            if (NativeLibraries.get().isEpubNativeAvailable()) {
                try (NativeArchive archive = NativeArchive.open(path)) {
                    CountingOutputStream countingOut = new CountingOutputStream(outputStream);
                    archive.streamEntry(entryName, countingOut);
                    return countingOut.getByteCount();
                } catch (IOException e) {
                    // If it's an IOException, it likely came from the destination stream
                    // (e.g. BoundedOutputStream limit), so we must NOT fall back.
                    throw e;
                } catch (Exception e) {
                    // If it's a format NativeArchive doesn't support (e.g. RAR if it's ZIP-only),
                    // or any other error, we fall back to nightcompress.
                    log.warn("NativeArchive streaming failed for {} (entry: {}), falling back to nightcompress. Reason: {}",
                            path.getFileName(), entryName, e.getMessage(), e);
                }
            }

            requireAvailable();

            // 2. Fallback to NightCompress (JNI).
            // We cannot directly use the NightCompress `InputStream` as it is limited
            // in its implementation and will cause fatal errors.  Instead, we can use
            // the `transferTo` on an output stream to copy data around.
            try (InputStream inputStream = Archive.getInputStream(path, entryName)) {
                if (inputStream != null) {
                    try {
                    return inputStream.transferTo(outputStream);
                } finally {
                    // NightCompress fails with a SIGSEGV if you do not read the
                    // entirety of the input stream from the zip.
                    inputStream.transferTo(OutputStream.nullOutputStream());
                }
                }
            } catch (Exception e) {
                throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
            }

            throw new IOException("Entry not found in archive");
        } finally {
            lock.unlock();
        }
    }

    public byte[] getEntryBytes(Path path, String entryName) throws IOException {
        try (
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ) {
            transferEntryTo(path, entryName, outputStream);

            return outputStream.toByteArray();
        }
    }

    /**
     * Reads at most {@code maxBytes} from the given archive entry.
     * This is used to read image headers for dimension detection without
     * loading the full (potentially multi-MB) image into memory.
     *
     * @return a byte array of at most {@code maxBytes} containing the
     *         leading bytes of the entry
     */
    public byte[] getEntryBytesPrefix(Path path, String entryName, int maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw ApiError.INVALID_INPUT.createException("maxBytes must be non-negative");
        }
        var bounded = new BoundedOutputStream(maxBytes);
        try {
            transferEntryTo(path, entryName, bounded);
        } catch (BoundedOutputStream.LimitReachedException _) {
            // expected, we only needed the prefix
        } catch (IOException e) {
            if (!(e.getCause() instanceof BoundedOutputStream.LimitReachedException)) {
                throw e;
            }
            // expected, we only needed the prefix
        }
        return bounded.toByteArray();
    }

    /**
     * OutputStream that captures at most {@code limit} bytes, then throws
     * {@link LimitReachedException} to short-circuit the transfer.
     */
    static final class BoundedOutputStream extends OutputStream {
        private final byte[] buf;
        private int count;

        BoundedOutputStream(int limit) {
            this.buf = new byte[limit];
        }

        @Override
        public void write(int b) throws IOException {
            if (count >= buf.length) throw new LimitReachedException();
            buf[count++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int remaining = buf.length - count;
            if (remaining <= 0) throw new LimitReachedException();
            int toCopy = Math.min(len, remaining);
            System.arraycopy(b, off, buf, count, toCopy);
            count += toCopy;
            if (toCopy < len) throw new LimitReachedException();
        }

        byte[] toByteArray() {
            return Arrays.copyOf(buf, count);
        }

        static final class LimitReachedException extends IOException {
            LimitReachedException() { super("Bounded output limit reached"); }
        }
    }

    public long extractEntryToPath(Path path, String entryName, Path outputPath) throws IOException {
        ReentrantLock lock = getFileLock(path);
        lock.lock();
        try {
            // Prefer NativeArchive (Panama)
            if (NativeLibraries.get().isEpubNativeAvailable()) {
                Path parent = outputPath.toAbsolutePath().getParent();
                String rawName = outputPath.getFileName().toString();
                String prefix = rawName.length() >= 3 ? rawName : rawName + "tmp";
                Path tmp = Files.createTempFile(parent, prefix, ".tmp");
                try (NativeArchive archive = NativeArchive.open(path);
                     OutputStream os = Files.newOutputStream(tmp)) {
                    CountingOutputStream countingOut = new CountingOutputStream(os);
                    archive.streamEntry(entryName, countingOut);
                    Files.move(tmp, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return countingOut.getByteCount();
                } catch (IOException e) {
                    Files.deleteIfExists(tmp);
                    throw e;
                } catch (Exception e) {
                    Files.deleteIfExists(tmp);
                    log.warn("NativeArchive extraction failed for {} (entry: {}), falling back to nightcompress. Reason: {}",
                            path.getFileName(), entryName, e.getMessage(), e);
                }
            }

            requireAvailable();

            boolean hasCreatedFile = false;
            try (OutputStream outputStream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                hasCreatedFile = true;

                return transferEntryTo(path, entryName, outputStream);
            } catch (Exception e) {
                if (hasCreatedFile) {
                    try {
                        Files.deleteIfExists(outputPath);
                    } catch (Exception ce) {
                        e.addSuppressed(ce);
                    }
                }

                throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Simple wrapper to count bytes written to an OutputStream.
     */
    private static class CountingOutputStream extends java.io.FilterOutputStream {
        private long byteCount = 0;

        public CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            byteCount++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            byteCount += len;
        }

        public long getByteCount() {
            return byteCount;
        }
    }
}
