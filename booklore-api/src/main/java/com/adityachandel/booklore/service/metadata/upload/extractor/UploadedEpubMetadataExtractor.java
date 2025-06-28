package com.adityachandel.booklore.service.metadata.upload.extractor;

import com.adityachandel.booklore.model.UploadedFileMetadata;
import io.documentnode.epub4j.domain.Author;
import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UploadedEpubMetadataExtractor implements UploadedFileMetadataExtractor {

    @Override
    public UploadedFileMetadata extractMetadata(String filePath) {
        UploadedFileMetadata metadata = new UploadedFileMetadata();

        try (FileInputStream fis = new FileInputStream(filePath)) {
            Book epub = new EpubReader().readEpub(fis);

            String title = epub.getTitle();
            if (title != null && !title.isBlank()) {
                metadata.setTitle(title);
            } else {
                metadata.setTitle(FilenameUtils.getBaseName(filePath));
            }

            List<Author> epubAuthors = epub.getMetadata().getAuthors();
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
