package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.settings.MetadataPersistenceSettings;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.model.entity.CategoryEntity;
import com.adityachandel.booklore.model.entity.MoodEntity;
import com.adityachandel.booklore.model.entity.TagEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.model.enums.MergeMetadataType;
import com.adityachandel.booklore.repository.AuthorRepository;
import com.adityachandel.booklore.repository.BookMetadataRepository;
import com.adityachandel.booklore.repository.CategoryRepository;
import com.adityachandel.booklore.repository.MoodRepository;
import com.adityachandel.booklore.repository.TagRepository;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.service.file.UnifiedFileMoveService;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriter;
import com.adityachandel.booklore.service.metadata.writer.MetadataWriterFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataManagementService {

    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final MoodRepository moodRepository;
    private final TagRepository tagRepository;
    private final BookMetadataRepository bookMetadataRepository;
    private final AppSettingService appSettingService;
    private final MetadataWriterFactory metadataWriterFactory;
    private final UnifiedFileMoveService unifiedFileMoveService;


    @Transactional
    public void consolidateMetadata(MergeMetadataType metadataType, List<String> targetValues, List<String> valuesToMerge) {

        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean moveFile = settings.isMoveFilesToLibraryPattern();
        boolean writeToFile = settings.isSaveToOriginalFile();

        switch (metadataType) {
            case authors -> consolidateAuthors(targetValues, valuesToMerge, writeToFile, moveFile);
            case categories -> consolidateCategories(targetValues, valuesToMerge, writeToFile, moveFile);
            case moods -> consolidateMoods(targetValues, valuesToMerge, writeToFile, moveFile);
            case tags -> consolidateTags(targetValues, valuesToMerge, writeToFile, moveFile);
            case series -> consolidateSeries(targetValues, valuesToMerge, writeToFile, moveFile);
            case publishers -> consolidatePublishers(targetValues, valuesToMerge, writeToFile, moveFile);
            case languages -> consolidateLanguages(targetValues, valuesToMerge, writeToFile, moveFile);
        }
    }

    private void writeMetadataToFile(List<BookMetadataEntity> metadataList, boolean moveFile) {
        for (BookMetadataEntity metadata : metadataList) {
            if (metadata.getBook() != null) {
                BookFileType bookType = metadata.getBook().getBookType();
                Optional<MetadataWriter> writerOpt = metadataWriterFactory.getWriter(bookType);
                writerOpt.ifPresent(writer -> {
                    File file = metadata.getBook().getFullFilePath().toFile();
                    writer.writeMetadataToFile(file, metadata, null, null);
                });

                if (moveFile) {
                    unifiedFileMoveService.moveSingleBookFile(metadata.getBook());
                }
            }
        }
    }

    private void consolidateAuthors(List<String> targetValues, List<String> valuesToMerge, boolean writeToFile, boolean moveFile) {
        List<AuthorEntity> targetAuthors = targetValues.stream()
                .map(name -> authorRepository.findByName(name)
                        .orElseGet(() -> {
                            AuthorEntity author = new AuthorEntity();
                            author.setName(name);
                            return authorRepository.save(author);
                        }))
                .toList();

        List<AuthorEntity> authorsToMerge = valuesToMerge.stream()
                .map(authorRepository::findByName)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (AuthorEntity oldAuthor : authorsToMerge) {
            List<BookMetadataEntity> booksWithOldAuthor = bookMetadataRepository.findAllByAuthorsContaining(oldAuthor);

            for (BookMetadataEntity metadata : booksWithOldAuthor) {
                metadata.getAuthors().remove(oldAuthor);
                for (AuthorEntity targetAuthor : targetAuthors) {
                    if (!metadata.getAuthors().contains(targetAuthor)) {
                        metadata.getAuthors().add(targetAuthor);
                    }
                }
            }

            bookMetadataRepository.saveAll(booksWithOldAuthor);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithOldAuthor, moveFile);
            }

            authorRepository.delete(oldAuthor);
        }

        log.info("Consolidated {} authors into {}: {}", authorsToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateCategories(List<String> targetValues, List<String> valuesToMerge, boolean writeToFile, boolean moveFile) {
        List<CategoryEntity> targetCategories = targetValues.stream()
                .map(name -> categoryRepository.findByNameIgnoreCase(name)
                        .map(existing -> {
                            existing.setName(name);
                            return categoryRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            CategoryEntity category = new CategoryEntity();
                            category.setName(name);
                            return categoryRepository.save(category);
                        }))
                .toList();

        List<CategoryEntity> categoriesToMerge = valuesToMerge.stream()
                .map(categoryRepository::findByNameIgnoreCase)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (CategoryEntity oldCategory : categoriesToMerge) {
            List<BookMetadataEntity> booksWithOldCategory = bookMetadataRepository.findAllByCategoriesContaining(oldCategory);

            for (BookMetadataEntity metadata : booksWithOldCategory) {
                metadata.getCategories().remove(oldCategory);
                for (CategoryEntity targetCategory : targetCategories) {
                    if (!metadata.getCategories().contains(targetCategory)) {
                        metadata.getCategories().add(targetCategory);
                    }
                }
            }

            bookMetadataRepository.saveAll(booksWithOldCategory);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithOldCategory, moveFile);
            }

            categoryRepository.delete(oldCategory);
        }

        log.info("Consolidated {} categories into {}: {}", categoriesToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateMoods(List<String> targetValues, List<String> valuesToMerge, boolean writeToFile, boolean moveFile) {
        List<MoodEntity> targetMoods = targetValues.stream()
                .map(name -> moodRepository.findByNameIgnoreCase(name)
                        .map(existing -> {
                            existing.setName(name);
                            return moodRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            MoodEntity mood = new MoodEntity();
                            mood.setName(name);
                            return moodRepository.save(mood);
                        }))
                .toList();

        List<MoodEntity> moodsToMerge = valuesToMerge.stream()
                .map(moodRepository::findByNameIgnoreCase)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (MoodEntity oldMood : moodsToMerge) {
            List<BookMetadataEntity> booksWithOldMood = bookMetadataRepository.findAllByMoodsContaining(oldMood);

            for (BookMetadataEntity metadata : booksWithOldMood) {
                metadata.getMoods().remove(oldMood);
                for (MoodEntity targetMood : targetMoods) {
                    if (!metadata.getMoods().contains(targetMood)) {
                        metadata.getMoods().add(targetMood);
                    }
                }
            }

            bookMetadataRepository.saveAll(booksWithOldMood);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithOldMood, moveFile);
            }

            moodRepository.delete(oldMood);
        }

        log.info("Consolidated {} moods into {}: {}", moodsToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateTags(List<String> targetValues, List<String> valuesToMerge, boolean writeToFile, boolean moveFile) {
        List<TagEntity> targetTags = targetValues.stream()
                .map(name -> tagRepository.findByNameIgnoreCase(name)
                        .map(existing -> {
                            existing.setName(name);
                            return tagRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            TagEntity tag = new TagEntity();
                            tag.setName(name);
                            return tagRepository.save(tag);
                        }))
                .toList();

        List<TagEntity> tagsToMerge = valuesToMerge.stream()
                .map(tagRepository::findByNameIgnoreCase)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (TagEntity oldTag : tagsToMerge) {
            List<BookMetadataEntity> booksWithOldTag = bookMetadataRepository.findAllByTagsContaining(oldTag);

            for (BookMetadataEntity metadata : booksWithOldTag) {
                metadata.getTags().remove(oldTag);
                for (TagEntity targetTag : targetTags) {
                    if (!metadata.getTags().contains(targetTag)) {
                        metadata.getTags().add(targetTag);
                    }
                }
            }

            bookMetadataRepository.saveAll(booksWithOldTag);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithOldTag, moveFile);
            }

            tagRepository.delete(oldTag);
        }

        log.info("Consolidated {} tags into {}: {}", tagsToMerge.size(), targetValues, valuesToMerge);
    }

    private void consolidateSeries(List<String> targetValues, List<String> valuesToMerge, boolean writeToFile, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Series merge requires exactly one target value");
        }
        String targetSeriesName = targetValues.get(0);

        for (String oldSeriesName : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldSeries = bookMetadataRepository.findAllBySeriesNameIgnoreCase(oldSeriesName);

            for (BookMetadataEntity metadata : booksWithOldSeries) {
                metadata.setSeriesName(targetSeriesName);
            }

            bookMetadataRepository.saveAll(booksWithOldSeries);

            if (writeToFile) {
                writeMetadataToFile(booksWithOldSeries, moveFile);
            }
        }

        log.info("Consolidated {} series into '{}': {}", valuesToMerge.size(), targetSeriesName, valuesToMerge);
    }

    private void consolidatePublishers(List<String> targetValues, List<String> valuesToMerge, boolean writeToFile, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Publisher merge requires exactly one target value");
        }
        String targetPublisher = targetValues.get(0);

        for (String oldPublisher : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldPublisher = bookMetadataRepository.findAllByPublisherIgnoreCase(oldPublisher);

            for (BookMetadataEntity metadata : booksWithOldPublisher) {
                metadata.setPublisher(targetPublisher);
            }

            bookMetadataRepository.saveAll(booksWithOldPublisher);

            if (writeToFile) {
                writeMetadataToFile(booksWithOldPublisher, moveFile);
            }
        }

        log.info("Consolidated {} publishers into '{}': {}", valuesToMerge.size(), targetPublisher, valuesToMerge);
    }

    private void consolidateLanguages(List<String> targetValues, List<String> valuesToMerge, boolean writeToFile, boolean moveFile) {
        if (targetValues.size() != 1) {
            throw new IllegalArgumentException("Language merge requires exactly one target value");
        }
        String targetLanguage = targetValues.get(0);

        for (String oldLanguage : valuesToMerge) {
            List<BookMetadataEntity> booksWithOldLanguage = bookMetadataRepository.findAllByLanguageIgnoreCase(oldLanguage);

            for (BookMetadataEntity metadata : booksWithOldLanguage) {
                metadata.setLanguage(targetLanguage);
            }

            bookMetadataRepository.saveAll(booksWithOldLanguage);

            if (writeToFile) {
                writeMetadataToFile(booksWithOldLanguage, moveFile);
            }
        }

        log.info("Consolidated {} languages into '{}': {}", valuesToMerge.size(), targetLanguage, valuesToMerge);
    }

    @Transactional
    public void deleteMetadata(MergeMetadataType metadataType, List<String> valuesToDelete) {
        MetadataPersistenceSettings settings = appSettingService.getAppSettings().getMetadataPersistenceSettings();
        boolean moveFile = settings.isMoveFilesToLibraryPattern();
        boolean writeToFile = settings.isSaveToOriginalFile();

        switch (metadataType) {
            case authors -> deleteAuthors(valuesToDelete, writeToFile, moveFile);
            case categories -> deleteCategories(valuesToDelete, writeToFile, moveFile);
            case moods -> deleteMoods(valuesToDelete, writeToFile, moveFile);
            case tags -> deleteTags(valuesToDelete, writeToFile, moveFile);
            case series -> deleteSeries(valuesToDelete, writeToFile, moveFile);
            case publishers -> deletePublishers(valuesToDelete, writeToFile, moveFile);
            case languages -> deleteLanguages(valuesToDelete, writeToFile, moveFile);
        }
    }

    private void deleteAuthors(List<String> valuesToDelete, boolean writeToFile, boolean moveFile) {
        List<AuthorEntity> authorsToDelete = valuesToDelete.stream()
                .map(authorRepository::findByName)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (AuthorEntity author : authorsToDelete) {
            List<BookMetadataEntity> booksWithAuthor = bookMetadataRepository.findAllByAuthorsContaining(author);

            for (BookMetadataEntity metadata : booksWithAuthor) {
                metadata.getAuthors().remove(author);
            }

            bookMetadataRepository.saveAll(booksWithAuthor);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithAuthor, moveFile);
            }

            authorRepository.delete(author);
        }

        log.info("Deleted {} authors: {}", authorsToDelete.size(), valuesToDelete);
    }

    private void deleteCategories(List<String> valuesToDelete, boolean writeToFile, boolean moveFile) {
        List<CategoryEntity> categoriesToDelete = valuesToDelete.stream()
                .map(categoryRepository::findByNameIgnoreCase)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (CategoryEntity category : categoriesToDelete) {
            List<BookMetadataEntity> booksWithCategory = bookMetadataRepository.findAllByCategoriesContaining(category);

            for (BookMetadataEntity metadata : booksWithCategory) {
                metadata.getCategories().remove(category);
            }

            bookMetadataRepository.saveAll(booksWithCategory);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithCategory, moveFile);
            }

            categoryRepository.delete(category);
        }

        log.info("Deleted {} categories: {}", categoriesToDelete.size(), valuesToDelete);
    }

    private void deleteMoods(List<String> valuesToDelete, boolean writeToFile, boolean moveFile) {
        List<MoodEntity> moodsToDelete = valuesToDelete.stream()
                .map(moodRepository::findByNameIgnoreCase)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (MoodEntity mood : moodsToDelete) {
            List<BookMetadataEntity> booksWithMood = bookMetadataRepository.findAllByMoodsContaining(mood);

            for (BookMetadataEntity metadata : booksWithMood) {
                metadata.getMoods().remove(mood);
            }

            bookMetadataRepository.saveAll(booksWithMood);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithMood, moveFile);
            }

            moodRepository.delete(mood);
        }

        log.info("Deleted {} moods: {}", moodsToDelete.size(), valuesToDelete);
    }

    private void deleteTags(List<String> valuesToDelete, boolean writeToFile, boolean moveFile) {
        List<TagEntity> tagsToDelete = valuesToDelete.stream()
                .map(tagRepository::findByNameIgnoreCase)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();

        for (TagEntity tag : tagsToDelete) {
            List<BookMetadataEntity> booksWithTag = bookMetadataRepository.findAllByTagsContaining(tag);

            for (BookMetadataEntity metadata : booksWithTag) {
                metadata.getTags().remove(tag);
            }

            bookMetadataRepository.saveAll(booksWithTag);
            bookMetadataRepository.flush();

            if (writeToFile) {
                writeMetadataToFile(booksWithTag, moveFile);
            }

            tagRepository.delete(tag);
        }

        log.info("Deleted {} tags: {}", tagsToDelete.size(), valuesToDelete);
    }

    private void deleteSeries(List<String> valuesToDelete, boolean writeToFile, boolean moveFile) {
        for (String seriesName : valuesToDelete) {
            List<BookMetadataEntity> booksWithSeries = bookMetadataRepository.findAllBySeriesNameIgnoreCase(seriesName);

            for (BookMetadataEntity metadata : booksWithSeries) {
                metadata.setSeriesName(null);
                metadata.setSeriesNumber(null);
                metadata.setSeriesTotal(null);
            }

            if (!booksWithSeries.isEmpty()) {
                bookMetadataRepository.saveAll(booksWithSeries);

                if (writeToFile) {
                    writeMetadataToFile(booksWithSeries, moveFile);
                }
            }
        }

        log.info("Deleted {} series: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deletePublishers(List<String> valuesToDelete, boolean writeToFile, boolean moveFile) {
        for (String publisher : valuesToDelete) {
            List<BookMetadataEntity> booksWithPublisher = bookMetadataRepository.findAllByPublisherIgnoreCase(publisher);

            for (BookMetadataEntity metadata : booksWithPublisher) {
                metadata.setPublisher(null);
            }

            if (!booksWithPublisher.isEmpty()) {
                bookMetadataRepository.saveAll(booksWithPublisher);

                if (writeToFile) {
                    writeMetadataToFile(booksWithPublisher, moveFile);
                }
            }
        }

        log.info("Deleted {} publishers: {}", valuesToDelete.size(), valuesToDelete);
    }

    private void deleteLanguages(List<String> valuesToDelete, boolean writeToFile, boolean moveFile) {
        for (String language : valuesToDelete) {
            List<BookMetadataEntity> booksWithLanguage = bookMetadataRepository.findAllByLanguageIgnoreCase(language);

            for (BookMetadataEntity metadata : booksWithLanguage) {
                metadata.setLanguage(null);
            }

            if (!booksWithLanguage.isEmpty()) {
                bookMetadataRepository.saveAll(booksWithLanguage);

                if (writeToFile) {
                    writeMetadataToFile(booksWithLanguage, moveFile);
                }
            }
        }

        log.info("Deleted {} languages: {}", valuesToDelete.size(), valuesToDelete);
    }
}
