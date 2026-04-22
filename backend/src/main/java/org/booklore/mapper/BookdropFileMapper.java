package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.BookdropFile;
import org.booklore.model.entity.BookdropFileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface BookdropFileMapper {

    @Mapping(target = "originalMetadata", source = "originalMetadata", qualifiedByName = "jsonToBookMetadata")
    @Mapping(target = "fetchedMetadata", source = "fetchedMetadata", qualifiedByName = "jsonToBookMetadata")
    BookdropFile toDto(BookdropFileEntity entity);

    @Named("jsonToBookMetadata")
    default BookMetadata jsonToBookMetadata(String json) {
        if (json == null || json.isBlank()) return null;
        return JsonMetadataMapper.parse(json);
    }
}