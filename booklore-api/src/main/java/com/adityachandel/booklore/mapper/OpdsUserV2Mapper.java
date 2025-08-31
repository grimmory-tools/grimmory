package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.entity.OpdsUserV2Entity;
import com.adityachandel.booklore.model.dto.OpdsUserV2;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OpdsUserV2Mapper {

    OpdsUserV2Mapper INSTANCE = Mappers.getMapper(OpdsUserV2Mapper.class);

    @Mapping(source = "user.id", target = "userId")
    OpdsUserV2 toDto(OpdsUserV2Entity entity);

    List<OpdsUserV2> toDto(List<OpdsUserV2Entity> entities);

    @Mapping(target = "user.id", source = "userId")
    OpdsUserV2Entity toEntity(OpdsUserV2 dto);
}
