package org.booklore.mapper;

import org.booklore.model.dto.BookReview;
import org.booklore.model.entity.BookReviewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookReviewMapper {

    BookReview toDto(BookReviewEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookMetadata", ignore = true)
    BookReviewEntity toEntity(BookReview dto);
}

