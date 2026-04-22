package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class JsonMetadataMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static BookMetadata parse(String json) {
        try {
            return objectMapper.readValue(json, BookMetadata.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    public static String toJson(BookMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException e) {
            return null;
        }
    }
}
