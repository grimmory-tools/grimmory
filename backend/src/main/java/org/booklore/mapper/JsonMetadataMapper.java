package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class JsonMetadataMapper {
    public static BookMetadata parse(String json) {
        try {
            return JsonMapper.shared().readValue(json, BookMetadata.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    public static String toJson(BookMetadata metadata) {
        try {
            return JsonMapper.shared().writeValueAsString(metadata);
        } catch (JacksonException e) {
            return null;
        }
    }
}
