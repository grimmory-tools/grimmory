package org.booklore.convertor;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.kobo.KoboSpanPositionMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Converter
@Slf4j
public class KoboSpanMapJsonConverter implements AttributeConverter<KoboSpanPositionMap, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(KoboSpanPositionMap attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JacksonException e) {
            log.error("Error converting Kobo span map to JSON", e);
            return null;
        }
    }

    @Override
    public KoboSpanPositionMap convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, KoboSpanPositionMap.class);
        } catch (JacksonException e) {
            log.error("Error converting JSON to Kobo span map", e);
            return null;
        }
    }
}
