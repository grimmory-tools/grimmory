package org.booklore.service.recommender;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookVectorService {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int VECTOR_DIMENSION = 128;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NON_ALPHANUMERIC_EXCEPT_SPACE_PATTERN = Pattern.compile("[^a-z0-9\\s]");

    public double[] generateEmbedding(BookEntity book) {
        if (book.getMetadata() == null) {
            return new double[VECTOR_DIMENSION];
        }

        BookMetadataEntity metadata = book.getMetadata();
        Map<String, Double> features = new HashMap<>();

        if (metadata.getTitle() != null) {
            addTextFeatures(features, "title", metadata.getTitle(), 3.0);
        }

        if (metadata.getAuthors() != null) {
            metadata.getAuthors().stream()
                    .map(AuthorEntity::getName)
                    .filter(Objects::nonNull)
                    .forEach(author -> features.put("author_" + author.toLowerCase(), 5.0));
        }

        if (metadata.getCategories() != null) {
            metadata.getCategories().stream()
                    .map(CategoryEntity::getName)
                    .filter(Objects::nonNull)
                    .forEach(cat -> features.put("category_" + cat.toLowerCase(), 4.0));
        }

        if (metadata.getSeriesName() != null) {
            features.put("series_" + metadata.getSeriesName().toLowerCase(), 6.0);
        }

        if (metadata.getPublisher() != null) {
            features.put("publisher_" + metadata.getPublisher().toLowerCase(), 2.0);
        }

        if (metadata.getDescription() != null) {
            addTextFeatures(features, "desc", metadata.getDescription(), 1.0);
        }

        return featuresToVector(features);
    }

    private void addTextFeatures(Map<String, Double> features, String prefix, String text, double weight) {
        String[] words = WHITESPACE_PATTERN.split(NON_ALPHANUMERIC_EXCEPT_SPACE_PATTERN.matcher(text.toLowerCase()).replaceAll(" "));

        Arrays.stream(words)
                .filter(w -> w.length() > 3)
                .limit(20)
                .forEach(word -> features.merge(prefix + "_" + word, weight, Double::sum));
    }

    private double[] featuresToVector(Map<String, Double> features) {
        double[] vector = new double[VECTOR_DIMENSION];

        for (Map.Entry<String, Double> entry : features.entrySet()) {
            int hash = Math.abs(entry.getKey().hashCode());
            int index = hash % VECTOR_DIMENSION;
            vector[index] += entry.getValue();
        }

        double norm = 0.0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    public String serializeVector(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JacksonException e) {
            log.error("Error serializing vector", e);
            return null;
        }
    }

    public double[] deserializeVector(String vectorJson) {
        if (vectorJson == null || vectorJson.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(vectorJson, double[].class);
        } catch (JacksonException e) {
            log.error("Error deserializing vector", e);
            return null;
        }
    }

    public double cosineSimilarity(double[] v1, double[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
        }

        return dotProduct;
    }

    public List<ScoredBook> findTopKSimilar(double[] targetVector, List<ScoredBook> candidates, int k) {
        if (targetVector == null) {
            return Collections.emptyList();
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(ScoredBook::getScore).reversed())
                .limit(k)
                .collect(Collectors.toList());
    }

    @Getter
    public static class ScoredBook {
        private final Long bookId;
        private final double score;

        public ScoredBook(Long bookId, double score) {
            this.bookId = bookId;
            this.score = score;
        }

    }
}

