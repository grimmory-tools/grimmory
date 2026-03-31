package org.booklore.service;

import com.github.gotson.nightcompress.Archive;
import com.github.gotson.nightcompress.ArchiveEntry;
import com.github.gotson.nightcompress.LibArchiveException;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class ArchiveService {
    @PostConstruct
    public void checkArchiveAvailable() {
        try {
            // We want to check the version early because it allows for
            // NightCompress to preload the libarchive library in a safe
            // thread.  Loading native libraries is not a thread safe operation.
            if (!Archive.isAvailable()) {
                log.warn("LibArchive is not available");
            }
        } catch (Throwable e) {
            log.error("LibArchive could not be loaded", e);
        }
    }

    public static boolean isAvailable() {
        return Archive.isAvailable();
    }

    public static class Entry {
        private Entry() {}

        @Getter
        private String name;

        @Getter
        private long size;
    }

    private Entry getEntryFromArchiveEntry(ArchiveEntry archiveEntry) {
        Entry entry = new Entry();

        entry.name = archiveEntry.getName();
        entry.size = archiveEntry.getSize();

        return entry;
    }

    public List<Entry> getEntries(Path path) throws IOException {
        return streamEntries(path).toList();
    }

    public Stream<Entry> streamEntries(Path path) throws IOException {
        try {
            return Archive.getEntries(path)
                    .stream()
                    .map(this::getEntryFromArchiveEntry);
        } catch (LibArchiveException e) {
            throw new IOException("Failed to read archive", e);
        }
    }

    public List<String> getEntryNames(Path path) throws IOException {
        return streamEntryNames(path).toList();
    }

    public Stream<String> streamEntryNames(Path path) throws IOException {
        try {
            return Archive.getEntries(path)
                    .stream()
                    .map(ArchiveEntry::getName);
        } catch (LibArchiveException e) {
            throw new IOException("Failed to read archive", e);
        }
    }

    public long transferEntryTo(Path path, String entryName, OutputStream outputStream) throws IOException {
        // We cannot directly use the NightCompress `InputStream` as it is limited
        // in its implementation and will cause fatal errors.  Instead, we can use
        // the `transferTo` on an output stream to copy data around.
        try (InputStream inputStream = Archive.getInputStream(path, entryName)) {
            if (inputStream != null) {
                return inputStream.transferTo(outputStream);
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
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
        try (InputStream inputStream = Archive.getInputStream(path, entryName)) {
            if (inputStream != null) {
                return Files.copy(inputStream, outputPath);
            }
        } catch (Exception e) {
            throw new IOException("Failed to extract from archive: " + e.getMessage(), e);
        }

        throw new IOException("Entry not found in archive");
    }
}
