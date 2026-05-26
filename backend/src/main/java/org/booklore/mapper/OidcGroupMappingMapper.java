package org.booklore.mapper;

import org.booklore.model.dto.OidcGroupMapping;
import org.booklore.model.entity.OidcGroupMappingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OidcGroupMappingMapper {
    @Mapping(target = "isAdmin", source = "admin")
    @Mapping(target = "permissions", expression = "java(jsonToStringList(entity.getPermissions()))")
    @Mapping(target = "libraryIds", expression = "java(jsonToLongList(entity.getLibraryIds()))")
    OidcGroupMapping toDto(OidcGroupMappingEntity entity);

    @Mapping(target = "admin", source = "isAdmin")
    @Mapping(target = "permissions", expression = "java(stringListToJson(dto.permissions()))")
    @Mapping(target = "libraryIds", expression = "java(longListToJson(dto.libraryIds()))")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OidcGroupMappingEntity toEntity(OidcGroupMapping dto);

    List<OidcGroupMapping> toDtoList(List<OidcGroupMappingEntity> entities);

    default List<String> jsonToStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        ObjectMapper objectMapper = JsonMapper.shared();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception _) {
            return List.of();
        }
    }

    default List<Long> jsonToLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        ObjectMapper objectMapper = JsonMapper.shared();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception _) {
            return List.of();
        }
    }

    default String stringListToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        ObjectMapper objectMapper = JsonMapper.shared();
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception _) {
            return "[]";
        }
    }

    default String longListToJson(List<Long> list) {
        if (list == null || list.isEmpty()) return "[]";
        ObjectMapper objectMapper = JsonMapper.shared();
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception _) {
            return "[]";
        }
    }
}
