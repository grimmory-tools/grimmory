package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookReview;
import com.adityachandel.booklore.model.entity.BookReviewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookReviewMapper {

    BookReview toDto(BookReviewEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookMetadata", ignore = true)
    BookReviewEntity toEntity(BookReview dto);
}

