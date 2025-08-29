package com.adityachandel.booklore.service.recommender;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.entity.AuthorEntity;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookMetadataEntity;
import com.adityachandel.booklore.repository.BookRepository;
import com.adityachandel.booklore.service.BookQueryService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service
public class BookRecommendationService {

    private final BookSimilarityService similarityService;
    private final BookRepository bookRepository;
    private final BookQueryService bookQueryService;
    private final BookMapper bookMapper;
    private final AuthenticationService authenticationService;

    private static final int MAX_BOOKS_PER_AUTHOR = 3;

    public List<BookRecommendation> getRecommendations(Long bookId, int limit) {
        BookEntity book = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        Set<BookRecommendationLite> recommendations = book.getSimilarBooksJson();
        if (recommendations == null || recommendations.isEmpty()) {
            log.info("Recommendations for book ID {} are missing or empty. Computing similarity...", bookId);
            recommendations = findSimilarBookIds(bookId, limit);
            book.setSimilarBooksJson(recommendations);
            bookRepository.save(book);
        }

        Set<Long> recommendedBookIds = recommendations.stream()
                .map(BookRecommendationLite::getB)
                .collect(Collectors.toSet());

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Set<Long> accessibleLibraryIds;
        if (user.getPermissions().isAdmin()) {
            accessibleLibraryIds = null;
        } else {
            accessibleLibraryIds = user.getAssignedLibraries().stream()
                    .map(Library::getId)
                    .collect(Collectors.toSet());
        }

        Map<Long, BookEntity> recommendedBooksMap = bookQueryService.findAllWithMetadataByIds(recommendedBookIds).stream()
                .filter(b -> {
                    if (accessibleLibraryIds == null) {
                        return true;
                    }
                    return b.getLibrary() != null && accessibleLibraryIds.contains(b.getLibrary().getId());
                })
                .collect(Collectors.toMap(BookEntity::getId, Function.identity()));

        return recommendations.stream()
                .map(rec -> {
                    BookEntity bookEntity = recommendedBooksMap.get(rec.getB());
                    if (bookEntity == null) return null;
                    return new BookRecommendation(bookMapper.toBookWithDescription(bookEntity, false), rec.getS());
                })
                .filter(Objects::nonNull)
                .limit(limit)
                .collect(Collectors.toList());
    }

    protected Set<BookRecommendationLite> findSimilarBookIds(Long bookId, int limit) {
        List<BookRecommendation> similarBooks = findSimilarBooks(bookId, limit);
        if (similarBooks == null || similarBooks.isEmpty()) {
            return Collections.emptySet();
        }
        return similarBooks.stream()
                .map(b -> new BookRecommendationLite(b.getBook().getId(), b.getSimilarityScore()))
                .collect(Collectors.toSet());
    }

    protected List<BookRecommendation> findSimilarBooks(Long bookId, int limit) {
        BookEntity target = bookRepository.findById(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        List<BookEntity> candidates = bookQueryService.getAllFullBookEntities();

        String targetSeriesName = Optional.ofNullable(target.getMetadata())
                .map(BookMetadataEntity::getSeriesName)
                .map(String::toLowerCase)
                .orElse(null);

        List<SimpleEntry<BookEntity, Double>> scored = candidates.stream()
                .filter(candidate -> !candidate.getId().equals(bookId))
                .filter(candidate -> {
                    String candidateSeriesName = Optional.ofNullable(candidate.getMetadata())
                            .map(BookMetadataEntity::getSeriesName)
                            .map(String::toLowerCase)
                            .orElse(null);
                    return targetSeriesName == null || !targetSeriesName.equals(candidateSeriesName);
                })
                .map(candidate -> new SimpleEntry<>(candidate, similarityService.calculateSimilarity(target, candidate)))
                .filter(entry -> entry.getValue() > 0.0)
                .sorted(Map.Entry.<BookEntity, Double>comparingByValue().reversed())
                .toList();

        Map<String, Integer> authorCounts = new HashMap<>();
        List<BookRecommendation> recommendations = new ArrayList<>();

        for (SimpleEntry<BookEntity, Double> entry : scored) {
            if (recommendations.size() >= limit) break;
            BookEntity book = entry.getKey();
            Set<String> authorNames = getAuthorNames(book);
            boolean allowed = authorNames.stream()
                    .allMatch(name -> authorCounts.getOrDefault(name, 0) < MAX_BOOKS_PER_AUTHOR);
            if (allowed) {
                Book dto = bookMapper.toBookWithDescription(book, false);
                recommendations.add(new BookRecommendation(dto, entry.getValue()));
                authorNames.forEach(name -> authorCounts.merge(name, 1, Integer::sum));
            }
        }

        return recommendations;
    }

    private Set<String> getAuthorNames(BookEntity book) {
        if (book.getMetadata() == null || book.getMetadata().getAuthors() == null) return Collections.emptySet();
        return book.getMetadata().getAuthors().stream()
                .map(AuthorEntity::getName)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}