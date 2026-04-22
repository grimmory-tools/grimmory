package org.booklore.service.migration.migrations;

import org.booklore.model.entity.BookEntity;
import org.booklore.repository.BookRepository;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.metadata.MetadataMatchService;
import org.booklore.service.migration.Migration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PopulateMetadataScoresMigration implements Migration {

    private final BookRepository bookRepository;
    private final BookQueryService bookQueryService;
    private final MetadataMatchService metadataMatchService;

    @Override
    public String getKey() {
        return "populateMetadataScores_v2";
    }

    @Override
    public String getDescription() {
        return "Calculate and store metadata match score for all books";
    }

    @Override
    public void execute() {
        log.info("Starting migration: {}", getKey());

        List<BookEntity> books = bookQueryService.getAllFullBookEntities();

        for (BookEntity book : books) {
            Float score = metadataMatchService.calculateMatchScore(book);
            book.setMetadataMatchScore(score);
        }

        bookRepository.saveAll(books);

        log.info("Migration '{}' applied to {} books.", getKey(), books.size());
    }
}
