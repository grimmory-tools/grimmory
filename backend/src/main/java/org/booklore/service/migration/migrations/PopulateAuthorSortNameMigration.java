package org.booklore.service.migration.migrations;

import org.booklore.model.entity.AuthorEntity;
import org.booklore.repository.AuthorRepository;
import org.booklore.service.migration.Migration;
import org.booklore.util.AuthorSortName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateAuthorSortNameMigration implements Migration {

    private final AuthorRepository authorRepository;

    @Override
    public String getKey() {
        return "populateAuthorSortName";
    }

    @Override
    public String getDescription() {
        return "Populate sort_name column for all authors";
    }

    @Override
    public void execute() {
        log.info("Starting migration: {}", getKey());

        int batchSize = 1000;
        int processedCount = 0;
        int page = 0;

        while (true) {
            Page<AuthorEntity> authorPage = authorRepository.findAll(PageRequest.of(page, batchSize));
            List<AuthorEntity> authors = authorPage.getContent();
            if (authors.isEmpty()) {
                break;
            }

            for (AuthorEntity author : authors) {
                if (!author.isSortNameLocked()) {
                    author.setSortName(AuthorSortName.compute(author.getName()));
                }
            }

            authorRepository.saveAll(authors);
            processedCount += authors.size();
            log.info("Migration progress: {} authors processed", processedCount);

            if (!authorPage.hasNext()) {
                break;
            }
            page++;
        }

        log.info("Completed migration '{}'. Total authors processed: {}", getKey(), processedCount);
    }
}
