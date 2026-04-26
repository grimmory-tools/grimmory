package org.grimmory.mapper;

import org.grimmory.model.dto.CustomFontDto;
import org.grimmory.model.entity.CustomFontEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomFontMapper {

    CustomFontDto toDto(CustomFontEntity entity);
}
