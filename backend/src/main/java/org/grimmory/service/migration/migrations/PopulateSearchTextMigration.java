package org.grimmory.service.migration.migrations;

import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.repository.BookRepository;
import org.grimmory.service.migration.Migration;
import org.grimmory.util.BookUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateSearchTextMigration implements Migration {

    private final BookRepository bookRepository;

    @Override
    public String getKey() {
        return "populateSearchText";
    }

    @Override
    public String getDescription() {
        return "Populate search_text column for all books";
    }

    @Override
    public void execute() {
        log.info("Starting migration: {}", getKey());

        int batchSize = 1000;
        int processedCount = 0;
        long lastId = 0;

        while (true) {
            List<BookEntity> bookBatch = bookRepository.findBooksForMigrationBatch(lastId, PageRequest.of(0, batchSize));
            if (bookBatch.isEmpty()) break;

            List<Long> bookIds = bookBatch.stream().map(BookEntity::getId).toList();
            List<BookEntity> books = bookRepository.findBooksWithMetadataAndAuthors(bookIds);

            for (BookEntity book : books) {
                BookMetadataEntity m = book.getMetadata();
                if (m != null) {
                    try {
                        m.setSearchText(BookUtils.buildSearchText(m));
                    } catch (Exception ex) {
                        log.warn("Failed to build search text for book {}: {}", book.getId(), ex.getMessage());
                    }
                }
            }

            bookRepository.saveAll(books);
            processedCount += books.size();
            lastId = bookBatch.getLast().getId();

            log.info("Migration progress: {} books processed", processedCount);

            if (bookBatch.size() < batchSize) break;
        }

        log.info("Completed migration '{}'. Total books processed: {}", getKey(), processedCount);
    }
}

