package org.booklore.service;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import com.github.junrar.exception.RarException;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.util.ArchiveUtils;
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
import java.util.stream.StreamSupport;
import java.util.Collections;

@Slf4j
@Service
public class ArchiveService {
    private static final int LOCK_STRIPE_COUNT = 256;
    private final ReentrantLock[] lockStripes = IntStream.range(0, LOCK_STRIPE_COUNT)
            .mapToObj(_ -> new ReentrantLock())
            .toArray(ReentrantLock[]::new);

    private ReentrantLock getFileLock(Path path) {
        int hash = path.toAbsolutePath().normalize().toString().hashCode();
        return lockStripes[Math.floorMod(hash, LOCK_STRIPE_COUNT)];
    }

    public record Entry(String name, long size) {}

    public List<Entry> getEntries(Path path) throws IOException {
        return streamEntries(path).toList();
    }

    private Stream<Entry> streamEntriesFromZip(Path path) throws IOException {
        try (ZipFile file = ZipFile.builder()
                .setPath(path)
                .get()) {

            return Collections.list(file.getEntries())
                    .stream()
                    .filter(e -> !e.isDirectory())
                    .map(e -> new Entry(e.getName(), e.getSize()));
        }
    }

    private Stream<Entry> streamEntriesFromRar(Path path) throws IOException {
        try (var archive = new com.github.junrar.Archive(path.toFile())) {
            return archive.getFileHeaders()
                    .stream()
                    .filter(h -> !h.isDirectory())
                    .map(h -> new Entry(h.getFileName(), h.getFullUnpackSize()))
                    .toList()
                    .stream();
        } catch (RarException rarException) {
            throw new IOException(rarException);
        }
    }

    private Stream<Entry> streamEntriesFrom7z(Path path) throws IOException {
        try (var sevenZFile = new SevenZFile.Builder().setPath(path).get()) {
            return StreamSupport.stream(sevenZFile.getEntries().spliterator(), false)
                    .map(entry -> new Entry(entry.getName(), entry.getSize()))
                    .toList()
                    .stream();
        }
    }

    public Stream<Entry> streamEntries(Path path) throws IOException {
        ReentrantLock lock = getFileLock(path);
        try {
            lock.lock();

            return switch(ArchiveUtils.detectArchiveType(path)) {
                case ZIP -> streamEntriesFromZip(path);
                case RAR -> streamEntriesFromRar(path);
                case SEVEN_ZIP -> streamEntriesFrom7z(path);
                case UNKNOWN -> throw new IOException("Unknown Archive Type");
            };
        } catch (IOException e) {
            throw new IOException("Failed to read archive", e);
        } finally {
            lock.unlock();
        }
    }

    public List<String> getEntryNames(Path path) throws IOException {
        return streamEntryNames(path).toList();
    }

    public Stream<String> streamEntryNames(Path path) throws IOException {
        return streamEntries(path).map(Entry::name);
    }

    private long transferZipEntryTo(Path path, String entryName, OutputStream outputStream) throws IOException {
        try (ZipFile zipFile = ZipFile.builder()
                .setPath(path)
                .get()) {

            ZipArchiveEntry entry = zipFile.getEntry(entryName);

            if (entry == null || entry.isDirectory()) {
                throw new IOException("Entry not found in archive");
            }

            try (InputStream is = zipFile.getInputStream(entry)) {
                return is.transferTo(outputStream);
            }
        }
    }

    private long transferRarEntryTo(Path path, String entryName, OutputStream outputStream) throws IOException {
        try (var archive = new com.github.junrar.Archive(path.toFile())) {
            var fileHeader = archive.getFileHeaders()
                    .stream()
                    .filter(h -> !h.isDirectory())
                    .filter(h -> entryName.equals(h.getFileName()))
                    .findFirst()
                    .orElse(null);

            if (fileHeader == null) {
                throw new IOException("Entry not found in archive");
            }

            try (InputStream is = archive.getInputStream(fileHeader)) {
                return is.transferTo(outputStream);
            }
        } catch (RarException rarException) {
            throw new IOException(rarException);
        }
    }

    private long transfer7zEntryTo(Path path, String entryName, OutputStream outputStream) throws IOException {
        try (var sevenZFile = new SevenZFile.Builder().setPath(path).get()) {
            var entry = StreamSupport.stream(sevenZFile.getEntries().spliterator(), false)
                    .filter(e -> entryName.equals(e.getName()))
                    .filter(SevenZArchiveEntry::hasStream)
                    .findFirst()
                    .orElse(null);

            if (entry == null) {
                throw new IOException("Entry not found in archive");
            }

            try (InputStream is = sevenZFile.getInputStream(entry)) {
                return is.transferTo(outputStream);
            }
        }
    }

    public long transferEntryTo(Path path, String entryName, OutputStream outputStream) throws IOException {
        ReentrantLock lock = getFileLock(path);
        try {
            lock.lock();

            return switch(ArchiveUtils.detectArchiveType(path)) {
                case ZIP -> transferZipEntryTo(path, entryName, outputStream);
                case RAR -> transferRarEntryTo(path, entryName, outputStream);
                case SEVEN_ZIP -> transfer7zEntryTo(path, entryName, outputStream);
                case UNKNOWN -> throw new IOException("Unknown Archive Type");
            };
        } catch (Exception e) {
            throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
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
        } finally {
            lock.unlock();
        }
    }
}
