package org.booklore.service.epub;

import org.booklore.repository.BookFileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class EpubRepairServiceTest {

    private static final byte[] MIMETYPE = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CONTAINER_XML = "<container/>".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDirectory;

    @Test
    void repairsMimetypeOrderAndCompressionAndCreatesAnOriginalBackup() throws IOException {
        Path epub = tempDirectory.resolve("book.epub");
        createEpub(epub, false);
        byte[] originalBytes = Files.readAllBytes(epub);

        EpubRepairService service = new EpubRepairService(mock(BookFileRepository.class));
        assertTrue(service.repairContainer(epub, "repair-task"));

        Path backup = tempDirectory.resolve("book.epub.repair-task.bak");
        assertArrayEquals(originalBytes, Files.readAllBytes(backup));
        try (ZipFile repaired = new ZipFile(epub.toFile())) {
            Enumeration<? extends ZipEntry> entries = repaired.entries();
            ZipEntry first = entries.nextElement();
            assertEquals("mimetype", first.getName());
            assertEquals(ZipEntry.STORED, first.getMethod());
            try (InputStream input = repaired.getInputStream(first)) {
                assertArrayEquals(MIMETYPE, input.readAllBytes());
            }
            try (InputStream input = repaired.getInputStream(repaired.getEntry("META-INF/container.xml"))) {
                assertArrayEquals(CONTAINER_XML, input.readAllBytes());
            }
        }
    }

    @Test
    void leavesCompliantEpubUnchangedWithoutCreatingBackup() throws IOException {
        Path epub = tempDirectory.resolve("compliant.epub");
        createEpub(epub, true);
        byte[] originalBytes = Files.readAllBytes(epub);

        EpubRepairService service = new EpubRepairService(mock(BookFileRepository.class));
        assertFalse(service.repairContainer(epub, "repair-task"));

        assertArrayEquals(originalBytes, Files.readAllBytes(epub));
        assertFalse(Files.exists(tempDirectory.resolve("compliant.epub.repair-task.bak")));
    }

    private void createEpub(Path epub, boolean compliant) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(epub))) {
            if (compliant) {
                writeStored(zip, "mimetype", MIMETYPE);
            } else {
                zip.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zip.write(CONTAINER_XML);
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("mimetype"));
                zip.write(MIMETYPE);
                zip.closeEntry();
            }
            if (compliant) {
                zip.putNextEntry(new ZipEntry("META-INF/container.xml"));
                zip.write(CONTAINER_XML);
                zip.closeEntry();
            }
        }
    }

    private void writeStored(ZipOutputStream zip, String name, byte[] contents) throws IOException {
        CRC32 crc = new CRC32();
        crc.update(contents);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(contents.length);
        entry.setCompressedSize(contents.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(contents);
        zip.closeEntry();
    }
}
