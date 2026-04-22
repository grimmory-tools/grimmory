package org.booklore.mapper;

import org.booklore.model.entity.TagEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TagMapper {

    default String toTagName(TagEntity tagEntity) {
        return tagEntity != null ? tagEntity.getName() : null;
    }

    default List<String> toTagNamesList(List<TagEntity> tagEntities) {
        if (tagEntities == null || tagEntities.isEmpty()) {
            return List.of();
        }
        return tagEntities.stream()
                .map(this::toTagName)
                .toList();
    }
}
