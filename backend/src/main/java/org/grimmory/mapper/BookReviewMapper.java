package org.grimmory.mapper;

import org.grimmory.model.dto.BookReview;
import org.grimmory.model.entity.BookReviewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookReviewMapper {

    BookReview toDto(BookReviewEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookMetadata", ignore = true)
    BookReviewEntity toEntity(BookReview dto);
}

