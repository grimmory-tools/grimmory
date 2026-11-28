package org.booklore.service.recommender;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.*;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.AuditAction;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.AuditLogRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.projection.BookEmbeddingProjection;
import org.booklore.service.book.BookQueryService;
import org.booklore.service.browse.BrowseService;
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
    private final BrowseService<BookEntity, Book> bookBrowse;
    private final AuthenticationService authenticationService;
    private final UserBookProgressRepository progressRepository;
    private final AuditLogRepository auditLogRepository;

    private static final int MAX_BOOKS_PER_AUTHOR = 3;

    @Transactional
    public List<Book> getRecommendations(Long bookId, int limit) {
        BookEntity book = bookRepository.findByIdWithMetadata(bookId).orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        Set<BookRecommendationLite> recommendations = book.getSimilarBooksJson();
        if (recommendations == null || recommendations.isEmpty()) {
            log.info("Recommendations for book ID {} are missing or empty. Computing similarity...", bookId);
            recommendations = findSimilarBookIds(bookId, limit);
            book.setSimilarBooksJson(recommendations);
            bookRepository.save(book);
        }

        List<Long> recommendedBookIds = personalizeRecommendations(recommendations, limit);

        return bookBrowse.findByIds(recommendedBookIds).stream().limit(limit).toList();
    }

    private List<Long> personalizeRecommendations(Set<BookRecommendationLite> recommendations, int limit) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        if (user == null || user.getId() == null) {
            return recommendations.stream()
                    .sorted(Comparator.comparingDouble(BookRecommendationLite::getS).reversed())
                    .limit(limit)
                    .map(BookRecommendationLite::getB)
                    .toList();
        }

        Set<ReadStatus> trackedStatuses = EnumSet.of(ReadStatus.READ, ReadStatus.RE_READING, ReadStatus.READING,
                ReadStatus.PARTIALLY_READ, ReadStatus.PAUSED, ReadStatus.WONT_READ, ReadStatus.ABANDONED);
        List<UserBookProgressEntity> interactions = progressRepository.findRecommendationInteractions(user.getId(), trackedStatuses);
        Map<String, Double> preferences = new HashMap<>();
        Set<Long> alreadyHandled = new HashSet<>();
        for (UserBookProgressEntity interaction : interactions) {
            BookEntity historyBook = interaction.getBook();
            if (historyBook == null || historyBook.getMetadata() == null) continue;
            if (interaction.getPersonalRating() != null || (interaction.getReadStatus() != null
                    && interaction.getReadStatus() != ReadStatus.UNREAD && interaction.getReadStatus() != ReadStatus.UNSET)) {
                alreadyHandled.add(historyBook.getId());
            }
            addBookPreference(preferences, historyBook, interactionWeight(interaction));
        }

        Set<Long> emailedBookIds = new HashSet<>(auditLogRepository.findBookIdsByUserIdAndAction(user.getId(), AuditAction.BOOK_SENT));
        if (!emailedBookIds.isEmpty()) {
            alreadyHandled.addAll(emailedBookIds);
            bookQueryService.findAllWithMetadataByIds(emailedBookIds)
                    .forEach(book -> addBookPreference(preferences, book, 0.25));
        }

        double maxPreference = preferences.values().stream().mapToDouble(Math::abs).max().orElse(0.0);
        Map<Long, BookEntity> candidatesById = bookQueryService.findAllWithMetadataByIds(
                        recommendations.stream().map(BookRecommendationLite::getB).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BookEntity::getId, Function.identity()));

        return recommendations.stream()
                .filter(candidate -> !alreadyHandled.contains(candidate.getB()))
                .map(candidate -> {
                    BookEntity candidateBook = candidatesById.get(candidate.getB());
                    double score = personalizedScore(candidate, candidateBook, preferences, maxPreference);
                    return new SimpleEntry<>(candidate.getB(), score);
                })
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(SimpleEntry::getKey)
                .toList();
    }

    private double interactionWeight(UserBookProgressEntity interaction) {
        Integer rating = interaction.getPersonalRating();
        if (rating != null) return Math.max(-1.0, Math.min(1.0, (rating - 3) / 2.0));
        ReadStatus status = interaction.getReadStatus();
        if (status == ReadStatus.WONT_READ || status == ReadStatus.ABANDONED) return -0.5;
        if (status == ReadStatus.READ || status == ReadStatus.RE_READING) return 0.5;
        return 0.25;
    }

    private void addBookPreference(Map<String, Double> preferences, BookEntity book, double weight) {
        if (weight == 0.0 || book.getMetadata() == null) return;
        BookMetadataEntity metadata = book.getMetadata();
        if (metadata.getAuthors() != null) metadata.getAuthors().stream()
                .map(AuthorEntity::getName).filter(Objects::nonNull)
                .forEach(name -> preferences.merge("author:" + name.toLowerCase(Locale.ROOT), weight, Double::sum));
        if (metadata.getCategories() != null) metadata.getCategories().stream()
                .map(CategoryEntity::getName).filter(Objects::nonNull)
                .forEach(name -> preferences.merge("category:" + name.toLowerCase(Locale.ROOT), weight, Double::sum));
        if (metadata.getSeriesName() != null && !metadata.getSeriesName().isBlank()) {
            preferences.merge("series:" + metadata.getSeriesName().toLowerCase(Locale.ROOT), weight, Double::sum);
        }
    }

    private double personalizedScore(BookRecommendationLite recommendation, BookEntity candidate,
                                     Map<String, Double> preferences, double maxPreference) {
        double affinity = 0.0;
        int matchedFeatures = 0;
        if (candidate != null && candidate.getMetadata() != null) {
            BookMetadataEntity metadata = candidate.getMetadata();
            if (metadata.getAuthors() != null) for (AuthorEntity author : metadata.getAuthors()) {
                if (author.getName() == null) continue;
                affinity += preferences.getOrDefault("author:" + author.getName().toLowerCase(Locale.ROOT), 0.0);
                matchedFeatures++;
            }
            if (metadata.getCategories() != null) for (CategoryEntity category : metadata.getCategories()) {
                if (category.getName() == null) continue;
                affinity += preferences.getOrDefault("category:" + category.getName().toLowerCase(Locale.ROOT), 0.0);
                matchedFeatures++;
            }
            if (metadata.getSeriesName() != null && !metadata.getSeriesName().isBlank()) {
                affinity += preferences.getOrDefault("series:" + metadata.getSeriesName().toLowerCase(Locale.ROOT), 0.0);
                matchedFeatures++;
            }
        }
        if (matchedFeatures > 0 && maxPreference > 0) {
            affinity = Math.max(-1.0, Math.min(1.0, affinity / matchedFeatures / maxPreference));
        } else {
            affinity = 0.0;
        }

        double personalAffinity = (affinity + 1.0) / 2.0;
        double publicRating = candidate == null || candidate.getMetadata() == null
                || candidate.getMetadata().getRating() == null
                ? 0.5 : Math.max(0.0, Math.min(1.0, candidate.getMetadata().getRating() / 5.0));
        double metadataConfidence = candidate == null || candidate.getMetadataMatchScore() == null
                ? 0.5 : Math.max(0.0, Math.min(1.0, candidate.getMetadataMatchScore() / 100.0));
        double confidenceAdjustedSimilarity = recommendation.getS() * (0.65 + 0.35 * metadataConfidence);
        return 0.70 * confidenceAdjustedSimilarity + 0.25 * personalAffinity + 0.05 * publicRating;
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
