package org.booklore.service.metadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.settings.LibraryFile;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.book.BookCreatorService;
import org.booklore.service.metadata.extractor.OpfMetadataExtractor;
import org.booklore.util.FileUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.booklore.util.FileService.truncate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdjacentOpfMetadataApplier {

    private final OpfMetadataExtractor opfMetadataExtractor;
    private final BookCreatorService bookCreatorService;

    public void applyAdjacentOpfMetadata(BookEntity bookEntity, LibraryFile libraryFile) {
        Optional<Path> opfPath = findAdjacentOpf(libraryFile);
        if (opfPath.isEmpty()) {
            return;
        }

        BookMetadata metadata = opfMetadataExtractor.extractMetadata(opfPath.get().toFile());
        applyMetadata(bookEntity, metadata);
    }

    private Optional<Path> findAdjacentOpf(LibraryFile libraryFile) {
        Path sourcePath = libraryFile.getFullPath();
        Path folder = libraryFile.isFolderBased() ? sourcePath : sourcePath.getParent();
        if (folder == null || !Files.isDirectory(folder)) {
            return Optional.empty();
        }

        List<String> preferredNames = new ArrayList<>();
        if (libraryFile.isFolderBased()) {
            preferredNames.add(sourcePath.getFileName().toString() + ".opf");
        } else {
            preferredNames.add(stem(sourcePath.getFileName().toString()) + ".opf");
        }
        preferredNames.add("metadata.opf");

        for (String preferredName : preferredNames) {
            Path directMatch = folder.resolve(preferredName);
            if (Files.isRegularFile(directMatch)) {
                return Optional.of(directMatch);
            }
        }

        try (var entries = Files.list(folder)) {
            List<Path> opfFiles = entries
                    .filter(Files::isRegularFile)
                    .filter(path -> "opf".equalsIgnoreCase(FileUtils.getExtension(path.getFileName().toString())))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();

            for (String preferredName : preferredNames) {
                for (Path candidate : opfFiles) {
                    if (candidate.getFileName().toString().equalsIgnoreCase(preferredName)) {
                        return Optional.of(candidate);
                    }
                }
            }

            if (opfFiles.size() == 1) {
                return Optional.of(opfFiles.getFirst());
            }
        } catch (Exception e) {
            log.debug("Failed to scan for adjacent OPF near '{}': {}", sourcePath, e.getMessage());
        }

        return Optional.empty();
    }

    private void applyMetadata(BookEntity bookEntity, BookMetadata metadata) {
        if (metadata == null || bookEntity == null || bookEntity.getMetadata() == null) {
            return;
        }

        if (StringUtils.isNotBlank(metadata.getTitle())) {
            bookEntity.getMetadata().setTitle(truncate(metadata.getTitle(), 1000));
        }
        if (metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
            bookEntity.getMetadata().getAuthors().clear();
            bookCreatorService.addAuthorsToBook(metadata.getAuthors(), bookEntity);
        }
        if (StringUtils.isNotBlank(metadata.getPublisher())) {
            bookEntity.getMetadata().setPublisher(truncate(metadata.getPublisher(), 1000));
        }
        if (metadata.getPublishedDate() != null) {
            bookEntity.getMetadata().setPublishedDate(metadata.getPublishedDate());
        }
        if (StringUtils.isNotBlank(metadata.getDescription())) {
            bookEntity.getMetadata().setDescription(truncate(metadata.getDescription(), 5000));
        }
        if (StringUtils.isNotBlank(metadata.getLanguage())) {
            bookEntity.getMetadata().setLanguage(truncate(metadata.getLanguage(), 10));
        }
        if (metadata.getCategories() != null && !metadata.getCategories().isEmpty()) {
            bookEntity.getMetadata().getCategories().clear();
            bookCreatorService.addCategoriesToBook(Set.copyOf(metadata.getCategories()), bookEntity);
        }
        if (StringUtils.isNotBlank(metadata.getIsbn10())) {
            bookEntity.getMetadata().setIsbn10(truncate(metadata.getIsbn10(), 10));
        }
        if (StringUtils.isNotBlank(metadata.getIsbn13())) {
            bookEntity.getMetadata().setIsbn13(truncate(metadata.getIsbn13(), 13));
        }
        if (StringUtils.isNotBlank(metadata.getSeriesName())) {
            bookEntity.getMetadata().setSeriesName(truncate(metadata.getSeriesName(), 1000));
        }
        if (metadata.getSeriesNumber() != null) {
            bookEntity.getMetadata().setSeriesNumber(metadata.getSeriesNumber());
        }
    }

    private String stem(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0) {
            return fileName;
        }
        return fileName.substring(0, lastDot);
    }
}
