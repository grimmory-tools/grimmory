package org.booklore.mapper;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.BookMetadataEntity;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class, CategoryMapper.class, MoodMapper.class, TagMapper.class, ComicMetadataMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookMetadataMapper {

    @AfterMapping
    default void mapWithDescriptionCondition(BookMetadataEntity bookMetadataEntity, @MappingTarget BookMetadata bookMetadata, @Context boolean includeDescription) {
        if (includeDescription) {
            bookMetadata.setDescription(bookMetadataEntity.getDescription());
        } else {
            bookMetadata.setDescription(null);
        }
    }

    @Named("withRelations")
    @Mapping(target = "description", ignore = true)
    BookMetadata toBookMetadata(BookMetadataEntity bookMetadataEntity, @Context boolean includeDescription);

    @Named("withoutRelations")
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "authors", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "moods", ignore = true)
    @Mapping(target = "tags", ignore = true)
    BookMetadata toBookMetadataWithoutRelations(BookMetadataEntity bookMetadataEntity, @Context boolean includeDescription);

    default BookMetadata toBookMetadata(BookMetadataEntity bookMetadataEntity) {
        return toBookMetadata(bookMetadataEntity, true);
    }

}