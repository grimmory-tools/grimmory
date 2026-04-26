package org.grimmory.mapper;

import org.grimmory.model.dto.BookNoteV2;
import org.grimmory.model.entity.BookNoteV2Entity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookNoteV2Mapper {

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = "user.id", target = "userId")
    BookNoteV2 toDto(BookNoteV2Entity entity);
}
