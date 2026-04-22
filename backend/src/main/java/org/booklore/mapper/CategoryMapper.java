package org.booklore.mapper;

import org.booklore.model.entity.CategoryEntity;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    default String toCategoryName(CategoryEntity categoryEntity) {
        return categoryEntity != null ? categoryEntity.getName() : null;
    }

    default List<String> toCategoryNamesList(List<CategoryEntity> categoryEntities) {
        if (categoryEntities == null || categoryEntities.isEmpty()) {
            return List.of();
        }
        return categoryEntities.stream()
                .map(this::toCategoryName)
                .toList();
    }
}
