package org.booklore.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchiveUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void detectArchiveType_ZipWithCbrExtension() throws IOException {
        File file = tempDir.resolve("test.cbr").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("test.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }

        assertEquals(ArchiveUtils.ArchiveType.ZIP, ArchiveUtils.detectArchiveType(file));
    }

    @Test
    void detectArchiveType_RarWithCbzExtension() throws IOException {
        File file = tempDir.resolve("test.cbz").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00});
        }

        assertEquals(ArchiveUtils.ArchiveType.RAR, ArchiveUtils.detectArchiveType(file));
    }

    @Test
    void detectArchiveType_SevenZip() throws IOException {
        File file = tempDir.resolve("test.cb7").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // 7z magic number: 37 7A BC AF 27 1C
            fos.write(new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C});
        }

        assertEquals(ArchiveUtils.ArchiveType.SEVEN_ZIP, ArchiveUtils.detectArchiveType(file));
    }

    @Test
    void detectArchiveType_FallbackToExtension() throws IOException {
        File file = tempDir.resolve("test.cbz").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[]{0, 0, 0, 0}); // Non-matching magic
        }

        assertEquals(ArchiveUtils.ArchiveType.ZIP, ArchiveUtils.detectArchiveType(file));
    }
}
