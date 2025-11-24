package com.adityachandel.booklore.service.metadata.extractor;

import com.adityachandel.booklore.model.dto.BookMetadata;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdfMetadataExtractorTest {

    private PdfMetadataExtractor extractor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        extractor = new PdfMetadataExtractor();
    }

    @Test
    void extractMetadata_shouldUseTitleFromMetadata_whenAvailable() throws IOException {
        // Arrange: Create a PDF with an explicit Title in metadata
        File pdfFile = tempDir.resolve("ignored-filename.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            PDDocumentInformation info = new PDDocumentInformation();
            info.setTitle("The Real Book Title");
            doc.setDocumentInformation(info);
            doc.save(pdfFile);
        }

        // Act
        BookMetadata result = extractor.extractMetadata(pdfFile);

        // Assert: Metadata title takes precedence over filename
        assertEquals("The Real Book Title", result.getTitle());
    }

    @Test
    void extractMetadata_shouldUseFilenameWithoutExtension_whenMetadataMissing() throws IOException {
        // Arrange: Create a PDF with NO metadata title
        // Name the file "Dune.pdf"
        File pdfFile = tempDir.resolve("Dune.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            // explicitly leaving metadata empty
            doc.save(pdfFile);
        }

        // Act
        BookMetadata result = extractor.extractMetadata(pdfFile);

        // Assert: The extension ".pdf" should be stripped
        assertEquals("Dune", result.getTitle());
    }

    @Test
    void extractMetadata_shouldHandleSpacesAndSpecialCharsInFilename() throws IOException {
        // Arrange
        File pdfFile = tempDir.resolve("Harry Potter and the Sorcerer's Stone.pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdfFile);
        }

        // Act
        BookMetadata result = extractor.extractMetadata(pdfFile);

        // Assert
        assertEquals("Harry Potter and the Sorcerer's Stone", result.getTitle());
    }
}
