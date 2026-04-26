package org.grimmory.mapper;

import org.grimmory.model.dto.Annotation;
import org.grimmory.model.entity.AnnotationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AnnotationMapper {

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "user.id", target = "userId")
    Annotation toDto(AnnotationEntity entity);
}
