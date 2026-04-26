package org.grimmory.mapper;

import org.grimmory.model.dto.BookMark;
import org.grimmory.model.entity.BookMarkEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookMarkMapper {

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "user.id", target = "userId")
    BookMark toDto(BookMarkEntity entity);
}
