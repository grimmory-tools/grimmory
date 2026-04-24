package org.booklore.service.recommender;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.*;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.projection.BookEmbeddingProjection;
import org.booklore.service.book.BookQueryService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
@Service
@Transactional(readOnly = true)
public class BookRecommendationService {

    private final BookSimilarityService similarityService;
    private final BookVectorService vectorService;
    private final BookRepository bookRepository;
    private final BookQueryService bookQueryService;
    private final BookMapper bookMapper;
    private final AuthenticationService authenticationService;

    private static final int MAX_BOOKS_PER_AUTHOR = 3;

    @Cacheable(value = "recommendations", key = "#bookId + '-' + #limit + '-' + @authenticationService.getAuthenticatedUser().id")
    @Transactional
    public List<BookRecommendation> getRecommendations(Long bookId, int limit) {
        BookEntity book = bookRepository.findByIdWithMetadata(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

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
        BookEntity target = bookRepository.findByIdWithMetadata(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        String targetVectorJson = Optional.ofNullable(target.getMetadata())
                .map(BookMetadataEntity::getEmbeddingVector)
                .orElse(null);

        if (targetVectorJson != null) {
            double[] targetVector = vectorService.deserializeVector(targetVectorJson);
            if (targetVector != null) {
                return findSimilarBooksViaEmbeddings(bookId, target, targetVector, limit);
            }
        }

        // Fallback: entity-based similarity (loads all candidates)
        return findSimilarBooksViaEntities(bookId, target, limit);
    }

    private List<BookRecommendation> findSimilarBooksViaEmbeddings(Long bookId, BookEntity target, double[] targetVector, int limit) {
        String targetSeriesName = Optional.ofNullable(target.getMetadata())
                .map(BookMetadataEntity::getSeriesName)
                .map(String::toLowerCase)
                .orElse(null);

        List<BookEmbeddingProjection> projections = bookRepository.findAllEmbeddingsForRecommendation(bookId);

        List<SimpleEntry<Long, Double>> scored = projections.stream()
                .filter(p -> {
                    if (targetSeriesName == null) return true;
                    String candidateSeries = Optional.ofNullable(p.getSeriesName())
                            .map(String::toLowerCase)
                            .orElse(null);
                    return !targetSeriesName.equals(candidateSeries);
                })
                .map(p -> {
                    double[] candidateVector = vectorService.deserializeVector(p.getEmbeddingVector());
                    double similarity = candidateVector != null
                            ? vectorService.cosineSimilarity(targetVector, candidateVector)
                            : 0.0;
                    return new SimpleEntry<>(p.getBookId(), similarity);
                })
                .filter(entry -> entry.getValue() > 0.1)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit * 3L)
                .toList();

        Set<Long> topBookIds = scored.stream().map(SimpleEntry::getKey).collect(Collectors.toSet());
        Map<Long, BookEntity> bookMap = bookQueryService.findAllWithMetadataByIds(topBookIds).stream()
                .collect(Collectors.toMap(BookEntity::getId, Function.identity()));

        Map<String, Integer> authorCounts = new HashMap<>();
        List<BookRecommendation> recommendations = new ArrayList<>();

        for (SimpleEntry<Long, Double> entry : scored) {
            if (recommendations.size() >= limit) break;
            BookEntity bookEntity = bookMap.get(entry.getKey());
            if (bookEntity == null) continue;
            Set<String> authorNames = getAuthorNames(bookEntity);
            boolean allowed = authorNames.stream()
                    .allMatch(name -> authorCounts.getOrDefault(name, 0) < MAX_BOOKS_PER_AUTHOR);
            if (allowed) {
                recommendations.add(new BookRecommendation(bookMapper.toBookWithDescription(bookEntity, false), entry.getValue()));
                authorNames.forEach(name -> authorCounts.merge(name, 1, Integer::sum));
            }
        }

        return recommendations;
    }

    private List<BookRecommendation> findSimilarBooksViaEntities(Long bookId, BookEntity target, int limit) {
        List<BookEntity> candidates = bookRepository.findAllForRecommendation(bookId);

        String targetSeriesName = Optional.ofNullable(target.getMetadata())
                .map(BookMetadataEntity::getSeriesName)
                .map(String::toLowerCase)
                .orElse(null);

        List<SimpleEntry<BookEntity, Double>> scored = candidates.stream()
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
