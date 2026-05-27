package org.booklore.mapper;

import org.booklore.model.dto.OidcGroupMapping;
import org.booklore.model.entity.OidcGroupMappingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class OidcGroupMappingMapper {

    @Autowired
    private ObjectMapper objectMapper;

    @Mapping(target = "isAdmin", source = "admin")
    @Mapping(target = "permissions", expression = "java(jsonToStringList(entity.getPermissions()))")
    @Mapping(target = "libraryIds", expression = "java(jsonToLongList(entity.getLibraryIds()))")
    public abstract OidcGroupMapping toDto(OidcGroupMappingEntity entity);

    @Mapping(target = "admin", source = "isAdmin")
    @Mapping(target = "permissions", expression = "java(stringListToJson(dto.permissions()))")
    @Mapping(target = "libraryIds", expression = "java(longListToJson(dto.libraryIds()))")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract OidcGroupMappingEntity toEntity(OidcGroupMapping dto);

    public abstract List<OidcGroupMapping> toDtoList(List<OidcGroupMappingEntity> entities);

    protected List<String> jsonToStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception _) {
            return List.of();
        }
    }

    protected List<Long> jsonToLongList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception _) {
            return List.of();
        }
    }

    protected String stringListToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception _) {
            return "[]";
        }
    }

    protected String longListToJson(List<Long> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception _) {
            return "[]";
        }
    }
}
