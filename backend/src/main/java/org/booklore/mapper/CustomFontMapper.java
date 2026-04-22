package org.booklore.mapper;

import org.booklore.model.dto.CustomFontDto;
import org.booklore.model.entity.CustomFontEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomFontMapper {

    CustomFontDto toDto(CustomFontEntity entity);
}
