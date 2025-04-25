package com.adityachandel.booklore.service;

import com.adityachandel.booklore.model.UploadedFileMetadata;
import com.adityachandel.booklore.util.FileUtils;
import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EpubMetadataExtractor implements BookFileMetadataExtractor {

    @Override
    public UploadedFileMetadata extractMetadata(String filePath) {
        UploadedFileMetadata metadata = new UploadedFileMetadata();

        try (FileInputStream fis = new FileInputStream(filePath)) {
            Book epubBook = new EpubReader().readEpub(fis);

            String title = epubBook.getTitle();
            if (title != null && !title.isBlank()) {
                metadata.setTitle(title);
            }

            List<Author> epubAuthors = epubBook.getMetadata().getAuthors();
            if (!epubAuthors.isEmpty()) {
                Set<String> authors = epubAuthors.stream()
                        .map(author -> author.getFirstname() + " " + author.getLastname())
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());

                metadata.setAuthors(authors);
            }

        } catch (IOException e) {
            log.error("Failed to extract metadata from EPUB file {}: {}", filePath, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while reading EPUB file {}: {}", filePath, e.getMessage());
        }
        return metadata;
    }
}
