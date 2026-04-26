package org.grimmory.mapper;

import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.dto.BookdropFile;
import org.grimmory.model.entity.BookdropFileEntity;
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