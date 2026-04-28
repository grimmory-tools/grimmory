package org.booklore.service.acquisition;

import org.booklore.model.dto.BookMetadata;
import org.booklore.repository.BookMetadataRepository;
import org.booklore.service.metadata.parser.OpenLibraryParser;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
@Slf4j
public class BookDiscoveryService {

    private final OpenLibraryParser openLibraryParser;
    private final BookMetadataRepository bookMetadataRepository;

    private final Cache<String, List<BookMetadata>> searchCache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public List<BookMetadata> searchBooks(String query, int page) {
        String cacheKey = query + "::page=" + page;
        return searchCache.get(cacheKey, k -> {
            log.info("Discovery search: query='{}', page={}", query, page);
            return openLibraryParser.searchByTerm(query, page);
        });
    }

    public List<BookMetadata> searchByIsbn(String isbn) {
        return searchCache.get("isbn:" + isbn, k -> {
            log.info("Discovery search: isbn='{}'", isbn);
            return openLibraryParser.searchByIsbn(isbn);
        });
    }

    public Set<String> getLibraryIsbn13s() {
        return bookMetadataRepository.findAllIsbn13s();
    }
}
