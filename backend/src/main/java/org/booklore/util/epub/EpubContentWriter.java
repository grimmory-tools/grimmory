package org.booklore.util.epub;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class EpubContentWriter {
    private static final String MIMETYPE_VALUE = "application/epub+zip";

    public static void createEpubFromDirectory(Path sourceDir, Path targetZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetZip))) {
            // EPUB spec requires mimetype to be the first entry in the ZIP, uncompressed (STORED)
            byte[] mimetypeData = MIMETYPE_VALUE.getBytes(StandardCharsets.UTF_8);
            ZipEntry mimetypeEntry = new ZipEntry("mimetype");
            mimetypeEntry.setMethod(ZipEntry.STORED);
            mimetypeEntry.setSize(mimetypeData.length);
            mimetypeEntry.setCompressedSize(mimetypeData.length);
            CRC32 crc = new CRC32();
            crc.update(mimetypeData);
            mimetypeEntry.setCrc(crc.getValue());
            zos.putNextEntry(mimetypeEntry);
            zos.write(mimetypeData);
            zos.closeEntry();

            Path mimetypeFile = sourceDir.resolve("mimetype");

            try (var pathStream = Files.walk(sourceDir)) {
                pathStream
                        .filter(path -> !path.equals(sourceDir))
                        .filter(path -> !path.equals(mimetypeFile))
                        .sorted()
                        .forEach(path -> {
                            try {
                                String relativePath = sourceDir.relativize(path).toString().replace(File.separatorChar, '/');
                                if (Files.isDirectory(path)) {
                                    zos.putNextEntry(new ZipEntry(relativePath + "/"));
                                    zos.closeEntry();
                                } else {
                                    zos.putNextEntry(new ZipEntry(relativePath));
                                    Files.copy(path, zos);
                                    zos.closeEntry();
                                }
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            }
        }
    }

}
