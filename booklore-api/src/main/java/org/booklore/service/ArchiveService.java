package org.booklore.service;

import com.github.gotson.nightcompress.Archive;
import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
public class ArchiveService {
    private static final int LOCK_STRIPE_COUNT = 256;
    private final ReentrantLock[] lockStripes = IntStream.range(0, LOCK_STRIPE_COUNT)
            .mapToObj(ignored -> new ReentrantLock())
            .toArray(ReentrantLock[]::new);
    private volatile boolean available = safeCheckAvailable();

    private static boolean safeCheckAvailable() {
        try {
            return Archive.isAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    private ReentrantLock getFileLock(Path path) {
        int hash = path.toAbsolutePath().normalize().toString().hashCode();
        return lockStripes[Math.floorMod(hash, LOCK_STRIPE_COUNT)];
    }

    @PostConstruct
    public void initLibArchive() {
        // Log the availability result that was already determined at construction
        // time.  The @PostConstruct runs on the Spring startup thread which also
        // ensures the native library is fully loaded before any concurrent HTTP
        // request thread can call into it.
        if (available) {
            log.info("LibArchive loaded successfully");
        } else {
            log.error("LibArchive is not available – CBX/archive features will not work");
        }
    }

    private void requireAvailable() throws IOException {
        if (!available) {
            throw new IOException("LibArchive is not available – cannot process archive");
        }
    }

    public static boolean isAvailable() {
        return Archive.isAvailable();
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
        requireAvailable();
        // We cannot directly use the NightCompress `InputStream` as it is limited
        // in its implementation and will cause fatal errors.  Instead, we can use
        // the `transferTo` on an output stream to copy data around.
        ReentrantLock lock = getFileLock(path);
        lock.lock();
        try (InputStream inputStream = Archive.getInputStream(path, entryName)) {
            if (inputStream != null) {
                return inputStream.transferTo(outputStream);
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }

        throw new IOException("Entry not found in archive");
    }

    public byte[] getEntryBytes(Path path, String entryName) throws IOException {
        try (
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ) {
            transferEntryTo(path, entryName, outputStream);

            return outputStream.toByteArray();
        }
    }

    public long extractEntryToPath(Path path, String entryName, Path outputPath) throws IOException {
        requireAvailable();
        ReentrantLock lock = getFileLock(path);
        lock.lock();
        try (InputStream inputStream = Archive.getInputStream(path, entryName)) {
            if (inputStream != null) {
                return Files.copy(inputStream, outputPath);
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
        } finally {
            lock.unlock();
        }

        throw new IOException("Entry not found in archive");
    }
}
