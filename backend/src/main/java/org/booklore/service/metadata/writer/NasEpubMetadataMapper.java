package org.booklore.service.metadata.writer;

import org.booklore.model.dto.BookMetadata;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NasEpubMetadataMapper {
    NasEpubMetadataMapper INSTANCE = Mappers.getMapper(NasEpubMetadataMapper.class);

    @Mapping(target = "comicMetadata", ignore = true)
    BookMetadata snapshot(BookMetadataEntity metadata);

    @Mapping(target = "comicMetadata", ignore = true)
    BookMetadataEntity restore(BookMetadata metadata);

    default String authorName(AuthorEntity author) {
        return author == null ? null : author.getName();
    }

    default String categoryName(CategoryEntity category) {
        return category == null ? null : category.getName();
    }

    default String moodName(MoodEntity mood) {
        return mood == null ? null : mood.getName();
    }

    default String tagName(TagEntity tag) {
        return tag == null ? null : tag.getName();
    }

    default AuthorEntity author(String name) {
        return name == null ? null : AuthorEntity.builder().name(name).build();
    }

    default CategoryEntity category(String name) {
        return name == null ? null : CategoryEntity.builder().name(name).build();
    }

    default MoodEntity mood(String name) {
        return name == null ? null : MoodEntity.builder().name(name).build();
    }

    default TagEntity tag(String name) {
        return name == null ? null : TagEntity.builder().name(name).build();
    }
}
