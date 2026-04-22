package org.booklore.convertor;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookRecommendationLite;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

@Converter
@Slf4j
public class BookRecommendationIdsListConverter implements AttributeConverter<Set<BookRecommendationLite>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Set<BookRecommendationLite>> SET_TYPE_REF = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Set<BookRecommendationLite> recommendations) {
        if (recommendations == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(recommendations);
        } catch (JacksonException e) {
            log.error("Failed to convert BookRecommendation set to JSON string: {}", recommendations, e);
            throw new RuntimeException("Error converting BookRecommendation list to JSON", e);
        }
    }

    @Override
    public Set<BookRecommendationLite> convertToEntityAttribute(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Set.of();
        }
        try {
            return objectMapper.readValue(json, SET_TYPE_REF);
        } catch (Exception e) {
            log.error("Corrupted similar_books_json found in database. Returning empty set. JSON: {}", json, e);
            return Set.of();
        }
    }
}
