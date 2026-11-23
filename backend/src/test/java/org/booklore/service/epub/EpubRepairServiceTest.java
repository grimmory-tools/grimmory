package org.booklore.service.epub;

import org.booklore.repository.BookFileRepository;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.file.BookFileMutationGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

        EpubRepairService service = new EpubRepairService(mock(BookFileRepository.class),
                mock(BookFileMutationGuard.class), mock(TransactionTemplate.class));
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

        EpubRepairService service = new EpubRepairService(mock(BookFileRepository.class),
                mock(BookFileMutationGuard.class), mock(TransactionTemplate.class));
        assertFalse(service.repairContainer(epub, "repair-task"));

        assertArrayEquals(originalBytes, Files.readAllBytes(epub));
        assertFalse(Files.exists(tempDirectory.resolve("compliant.epub.repair-task.bak")));
    }

    @Test
    void repairsEachFileInsideGuardedTransactionAndReportsFingerprintFailure() throws IOException {
        Path epub = tempDirectory.resolve("book.epub");
        createEpub(epub, false);
        var files = mock(BookFileRepository.class);
        var guard = mock(BookFileMutationGuard.class);
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(files.countActiveBookFilesByType(BookFileType.EPUB)).thenReturn(1L);
        when(files.findActiveBookFileIdsByTypeAfter(anyLong(), eq(BookFileType.EPUB), any()))
                .thenReturn(List.of(1L), List.of());
        var file = mock(BookFileEntity.class);
        when(file.getFullFilePath()).thenReturn(epub);
        when(files.findAllForRepairByIdIn(List.of(1L), BookFileType.EPUB)).thenReturn(List.of(file));
        when(files.updateFingerprintAndFileSize(eq(1L), anyString(), anyLong())).thenReturn(0);

        var result = new EpubRepairService(files, guard, transactions)
                .repairAll("repair-task", () -> false, progress -> {});

        assertEquals(1, result.failedCount());
        assertEquals(0, result.repairedCount());
        assertTrue(Files.exists(tempDirectory.resolve("book.epub.repair-task.bak")));
        var order = inOrder(guard, files);
        order.verify(guard).beforeMutation();
        order.verify(files).findAllForRepairByIdIn(List.of(1L), BookFileType.EPUB);
        order.verify(files).updateFingerprintAndFileSize(eq(1L), anyString(), anyLong());
    }

    @Test
    void repairAllUpdatesFingerprintAndCountsRepairedAndUnchangedFiles() throws IOException {
        Path damaged = tempDirectory.resolve("damaged.epub");
        Path compliant = tempDirectory.resolve("compliant.epub");
        createEpub(damaged, false);
        createEpub(compliant, true);
        var files = mock(BookFileRepository.class);
        var transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(files.countActiveBookFilesByType(BookFileType.EPUB)).thenReturn(2L);
        when(files.findActiveBookFileIdsByTypeAfter(anyLong(), eq(BookFileType.EPUB), any()))
                .thenReturn(List.of(1L, 2L), List.of());
        var damagedFile = mock(BookFileEntity.class);
        var compliantFile = mock(BookFileEntity.class);
        when(damagedFile.getFullFilePath()).thenReturn(damaged);
        when(compliantFile.getFullFilePath()).thenReturn(compliant);
        when(files.findAllForRepairByIdIn(List.of(1L), BookFileType.EPUB)).thenReturn(List.of(damagedFile));
        when(files.findAllForRepairByIdIn(List.of(2L), BookFileType.EPUB)).thenReturn(List.of(compliantFile));
        when(files.updateFingerprintAndFileSize(eq(1L), anyString(), anyLong())).thenReturn(1);

        var result = new EpubRepairService(files, mock(BookFileMutationGuard.class), transactions)
                .repairAll("repair-task", () -> false, progress -> {});

        assertEquals(1, result.repairedCount());
        assertEquals(1, result.unchangedCount());
        assertEquals(0, result.failedCount());
        verify(files).updateFingerprintAndFileSize(eq(1L), anyString(), anyLong());
        verify(files, never()).updateFingerprintAndFileSize(eq(2L), anyString(), anyLong());
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
