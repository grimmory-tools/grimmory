package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.UploadedFileMetadata;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Slf4j
@Service
public class PdfMetadataExtractor implements BookFileMetadataExtractor {

    @Override
    public UploadedFileMetadata extractMetadata(String filePath) {
        File file = new File(filePath);
        UploadedFileMetadata metadata = new UploadedFileMetadata();
        try (PDDocument pdf = Loader.loadPDF(file)) {
            PDDocumentInformation info = pdf.getDocumentInformation();
            if (info != null) {
                if (info.getTitle() != null && !info.getTitle().isBlank()) {
                    metadata.setTitle(info.getTitle());
                }
                if (info.getAuthor() != null && !info.getAuthor().isBlank()) {
                    metadata.setAuthors(Set.of(info.getAuthor().split(",")));
                }
            } else {
                log.warn("No document information found in: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Error loading PDF file: {}, error: {}", filePath, e.getMessage());
        }
        return metadata;
    }
}
