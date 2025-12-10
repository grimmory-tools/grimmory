package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.BookMark;
import com.adityachandel.booklore.model.entity.BookMarkEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookMarkMapper {
    BookMark toDto(BookMarkEntity entity);
}
