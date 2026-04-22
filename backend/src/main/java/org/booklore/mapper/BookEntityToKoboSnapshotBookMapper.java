package org.booklore.mapper;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.KoboSnapshotBookEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookEntityToKoboSnapshotBookMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookId", expression = "java(book.getId())")
    @Mapping(target = "synced", constant = "false")
    KoboSnapshotBookEntity toKoboSnapshotBook(BookEntity book);
}
