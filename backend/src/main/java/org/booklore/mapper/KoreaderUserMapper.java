package org.booklore.mapper;

import org.booklore.model.dto.KoreaderUser;
import org.booklore.model.entity.KoreaderUserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KoreaderUserMapper {

    KoreaderUserMapper INSTANCE = Mappers.getMapper(KoreaderUserMapper.class);

    KoreaderUser toDto(KoreaderUserEntity entity);

    KoreaderUserEntity toEntity(KoreaderUser dto);

    List<KoreaderUser> toDtoList(List<KoreaderUserEntity> entities);
}
