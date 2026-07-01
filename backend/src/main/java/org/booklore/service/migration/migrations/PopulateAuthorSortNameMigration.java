package org.booklore.service.migration.migrations;

import org.booklore.repository.AuthorRepository;
import org.booklore.service.migration.Migration;
import org.booklore.util.AuthorSortName;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateAuthorSortNameMigration implements Migration {

    private final AuthorRepository authorRepository;
    private final EntityManager entityManager;

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

        var batchSize = 1000;
        var processedCount = 0;
        var page = 0;

        while (true) {
            // Stable id ordering keeps offset paging consistent as rows are updated each batch.
            var authors = authorRepository.findAll(PageRequest.of(page, batchSize, Sort.by("id"))).getContent();
            if (authors.isEmpty()) {
                break;
            }

            for (var author : authors) {
                if (!author.isSortNameLocked()) {
                    author.setSortName(AuthorSortName.compute(author.getName()));
                }
            }

            authorRepository.saveAll(authors);
            processedCount += authors.size();
            log.info("Migration progress: {} authors processed", processedCount);

            // Flush and detach the batch so the persistence context does not grow across the scan.
            entityManager.flush();
            entityManager.clear();

            if (authors.size() < batchSize) {
                break;
            }
            page++;
        }

        log.info("Completed migration '{}'. Total authors processed: {}", getKey(), processedCount);
    }
}
