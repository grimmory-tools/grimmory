package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookdropFile;
import org.booklore.model.entity.BookdropFileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Mapper(componentModel = "spring")
public abstract class BookdropFileMapper {

    @Autowired
    private ObjectMapper objectMapper;

    @Mapping(target = "originalMetadata", source = "originalMetadata", qualifiedByName = "jsonToBookMetadata")
    @Mapping(target = "fetchedMetadata", source = "fetchedMetadata", qualifiedByName = "jsonToBookMetadata")
    public abstract BookdropFile toDto(BookdropFileEntity entity);

    @Named("jsonToBookMetadata")
    protected BookMetadata jsonToBookMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(json, BookMetadata.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}