package org.booklore.service.recommender;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class BookSimilarityService {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC_EXCEPT_SPACE_PATTERN = Pattern.compile("[^a-z0-9 ]");

    @Getter
    public enum SimilarityWeight {
        TITLE(1.5),
        SERIES(2.0),
        AUTHORS(3.0),
        CATEGORIES(3.5),
        RATING(0.6);

        private final double weight;

        SimilarityWeight(double weight) {
            this.weight = weight;
        }
    }

    public double calculateSimilarity(BookEntity a, BookEntity b) {
        if (a.getMetadata() == null || b.getMetadata() == null) {
            return 0.0;
        }

        BookMetadataEntity metaA = a.getMetadata();
        BookMetadataEntity metaB = b.getMetadata();

        double score = 0;

        score += SimilarityWeight.AUTHORS.getWeight() *
                jaccardSimilarity(extractNames(metaA.getAuthors()), extractNames(metaB.getAuthors()));

        score += SimilarityWeight.CATEGORIES.getWeight() *
                jaccardSimilarity(extractNames(metaA.getCategories()), extractNames(metaB.getCategories()));

        score += SimilarityWeight.TITLE.getWeight() *
                cosineSimilarity(tokenize(metaA.getTitle()), tokenize(metaB.getTitle()));

        if (isEqualIgnoreCase(metaA.getSeriesName(), metaB.getSeriesName())) {
            score += SimilarityWeight.SERIES.getWeight();
        }

        score += SimilarityWeight.RATING.getWeight() *
                ratingSimilarity(metaA.getRating(), metaB.getRating());

        return round(score, 5);
    }

    private Set<String> extractNames(Collection<?> entities) {
        if (entities == null) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        for (Object obj : entities) {
            if (obj instanceof AuthorEntity author && author.getName() != null) {
                names.add(author.getName().toLowerCase());
            } else if (obj instanceof CategoryEntity category && category.getName() != null) {
                names.add(category.getName().toLowerCase());
            }
        }
        return names;
    }

    private boolean isEqualIgnoreCase(String s1, String s2) {
        return s1 != null && s2 != null && s1.equalsIgnoreCase(s2);
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Set<String> setA = new HashSet<>(a);
        Set<String> setB = new HashSet<>(b);

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Map<String, Integer> tokenize(String text) {
        Map<String, Integer> vector = new HashMap<>();
        if (text == null || text.isBlank()) return vector;

        String[] tokens = WHITESPACE_PATTERN.split(NON_ALPHANUMERIC_EXCEPT_SPACE_PATTERN.matcher(text.toLowerCase()).replaceAll(""));
        for (String token : tokens) {
            vector.put(token, vector.getOrDefault(token, 0) + 1);
        }
        return vector;
    }

    private double cosineSimilarity(Map<String, Integer> vecA, Map<String, Integer> vecB) {
        if (vecA.isEmpty() || vecB.isEmpty()) return 0.0;

        Set<String> keys = new HashSet<>();
        keys.addAll(vecA.keySet());
        keys.addAll(vecB.keySet());

        double dotProduct = 0, normA = 0, normB = 0;

        for (String key : keys) {
            int a = vecA.getOrDefault(key, 0);
            int b = vecB.getOrDefault(key, 0);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        return (normA == 0 || normB == 0) ? 0.0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double ratingSimilarity(Double a, Double b) {
        if (a == null || b == null) return 0.0;
        double diff = Math.abs(a - b);
        return 1.0 - Math.min(diff / 5.0, 1.0);
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        return Math.round(value * factor) / (double) factor;
    }
}