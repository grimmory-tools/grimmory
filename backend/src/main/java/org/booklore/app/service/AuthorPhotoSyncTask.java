package org.booklore.app.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.AppSettingEntity;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.repository.AppSettingsRepository;
import org.booklore.repository.AuthorRepository;
import org.booklore.util.FileService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@AllArgsConstructor
public class AuthorPhotoSyncTask implements CommandLineRunner {

    private final AuthorRepository authorRepository;
    private final FileService fileService;
    private final TransactionTemplate transactionTemplate;
    private final AppSettingsRepository appSettingsRepository;

    private static final String AUTHOR_PHOTO_SYNC_COMPLETED = "author_photo_sync_completed";

    @Override
    public void run(String... args) {
        if (isSyncNeeded()) {
            log.info("Scheduling author photo synchronization task in background...");
            CompletableFuture.runAsync(this::syncPhotos);
        } else {
            log.info("Author photo synchronization already completed. Skipping.");
        }
    }

    private boolean isSyncNeeded() {
        AppSettingEntity setting = appSettingsRepository.findByName(AUTHOR_PHOTO_SYNC_COMPLETED);
        return setting == null || !"true".equalsIgnoreCase(setting.getVal());
    }

    private void syncPhotos() {
        log.info("Starting author photo synchronization...");
        int page = 0;
        int size = 500;
        long totalUpdated = 0;
        Page<AuthorEntity> authorPage;

        try {
            do {
                authorPage = authorRepository.findAll(PageRequest.of(page, size));
                long updatedInBatch = processBatch(authorPage.getContent());
                totalUpdated += updatedInBatch;
                
                if (authorPage.hasNext()) {
                    log.debug("Processed batch {}. Total authors processed: {}", page + 1, (page + 1) * size);
                }
                page++;
            } while (authorPage.hasNext());

            if (totalUpdated > 0) {
                log.info("Finished author photo synchronization. Updated {} authors.", totalUpdated);
            } else {
                log.info("Author photo synchronization finished. No changes needed.");
            }
            setSyncCompleted();
        } catch (Exception e) {
            log.error("Error during author photo synchronization", e);
        }
    }

    private void setSyncCompleted() {
        transactionTemplate.execute(status -> {
            AppSettingEntity setting = appSettingsRepository.findByName(AUTHOR_PHOTO_SYNC_COMPLETED);
            if (setting == null) {
                setting = new AppSettingEntity();
                setting.setName(AUTHOR_PHOTO_SYNC_COMPLETED);
            }
            setting.setVal("true");
            appSettingsRepository.save(setting);
            return null;
        });
    }

    private long processBatch(List<AuthorEntity> authors) {
        Long updatedCount = transactionTemplate.execute(status -> {
            long count = 0;
            for (AuthorEntity author : authors) {
                String thumbnailPath = fileService.getAuthorThumbnailFile(author.getId());
                boolean actuallyHasPhoto = thumbnailPath != null && Files.exists(Paths.get(thumbnailPath));

                if (author.isHasPhoto() != actuallyHasPhoto) {
                    author.setHasPhoto(actuallyHasPhoto);
                    authorRepository.save(author);
                    count++;
                }
            }
            return count;
        });
        return updatedCount != null ? updatedCount : 0;
    }
}
