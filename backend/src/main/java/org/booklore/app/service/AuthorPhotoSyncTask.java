package org.booklore.app.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.AuthorEntity;
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

    @Override
    public void run(String... args) {
        log.info("Scheduling author photo synchronization task in background...");
        CompletableFuture.runAsync(this::syncPhotos);
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
        } catch (Exception e) {
            log.error("Error during author photo synchronization", e);
        }
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
