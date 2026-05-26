package org.booklore.mapper;

import org.booklore.model.dto.kobo.KoboReadingState;
import org.booklore.model.entity.KoboReadingStateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KoboReadingStateMapper {

    ObjectMapper objectMapper = new ObjectMapper();
    Pattern SURROUNDING_DOUBLE_QUOTES_PATTERN = Pattern.compile("^\"|\"$");

    @Mapping(target = "currentBookmarkJson", expression = "java(toJson(dto.getCurrentBookmark()))")
    @Mapping(target = "statisticsJson", expression = "java(toJson(dto.getStatistics()))")
    @Mapping(target = "statusInfoJson", expression = "java(toJson(dto.getStatusInfo()))")
    @Mapping(target = "entitlementId", expression = "java(cleanString(dto.getEntitlementId()))")
    @Mapping(target = "created", expression = "java(dto.getCreated())")
    @Mapping(target = "lastModifiedString", expression = "java(dto.getLastModified())")
    @Mapping(target = "lastModified", ignore = true)
    @Mapping(target = "priorityTimestamp", expression = "java(dto.getPriorityTimestamp())")
    KoboReadingStateEntity toEntity(KoboReadingState dto);

    @Mapping(target = "currentBookmark", expression = "java(fromJson(entity.getCurrentBookmarkJson(), KoboReadingState.CurrentBookmark.class))")
    @Mapping(target = "statistics", expression = "java(fromJson(entity.getStatisticsJson(), KoboReadingState.Statistics.class))")
    @Mapping(target = "statusInfo", expression = "java(fromJson(entity.getStatusInfoJson(), KoboReadingState.StatusInfo.class))")
    @Mapping(target = "entitlementId", expression = "java(cleanString(entity.getEntitlementId()))")
    @Mapping(target = "created", expression = "java(entity.getCreated())")
    @Mapping(target = "lastModified", expression = "java(entity.getLastModifiedString())")
    @Mapping(target = "priorityTimestamp", expression = "java(entity.getPriorityTimestamp())")
    KoboReadingState toDto(KoboReadingStateEntity entity);

    default String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    default <T> T fromJson(String json, Class<T> clazz) {
        try {
            return json == null ? null : objectMapper.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to deserialize JSON", e);
        }
    }

    default String cleanString(String value) {
        if (value == null) return null;
        return SURROUNDING_DOUBLE_QUOTES_PATTERN.matcher(value).replaceAll("");
    }
}