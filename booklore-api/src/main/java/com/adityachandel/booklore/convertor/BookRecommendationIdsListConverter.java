package com.adityachandel.booklore.convertor;

import com.adityachandel.booklore.model.dto.BookRecommendationLite;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

@Converter
@Slf4j
public class BookRecommendationIdsListConverter implements AttributeConverter<Set<BookRecommendationLite>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<Set<BookRecommendationLite>> SET_TYPE_REF = new TypeReference<>() {};

    static {
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String convertToDatabaseColumn(Set<BookRecommendationLite> recommendations) {
        if (recommendations == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(recommendations);
        } catch (JsonProcessingException e) {
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
