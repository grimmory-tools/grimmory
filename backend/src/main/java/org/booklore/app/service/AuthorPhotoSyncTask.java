package org.booklore.app.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.repository.AuthorRepository;
import org.booklore.util.FileService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Component
@AllArgsConstructor
public class AuthorPhotoSyncTask implements CommandLineRunner {

    private final AuthorRepository authorRepository;
    private final FileService fileService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting author photo synchronization task...");
        List<AuthorEntity> authors = authorRepository.findAll();
        long updatedCount = 0;

        for (AuthorEntity author : authors) {
            String thumbnailPath = fileService.getAuthorThumbnailFile(author.getId());
            boolean actuallyHasPhoto = thumbnailPath != null && Files.exists(Paths.get(thumbnailPath));
            
            if (author.isHasPhoto() != actuallyHasPhoto) {
                author.setHasPhoto(actuallyHasPhoto);
                authorRepository.save(author);
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            log.info("Finished author photo synchronization. Updated {} authors.", updatedCount);
        } else {
            log.info("Author photo synchronization finished. No changes needed.");
        }
    }
}
