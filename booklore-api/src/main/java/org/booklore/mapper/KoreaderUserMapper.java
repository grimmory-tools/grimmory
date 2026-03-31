package org.booklore.mapper;

import org.booklore.model.dto.KoreaderUser;
import org.booklore.model.entity.KoreaderUserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KoreaderUserMapper {

    KoreaderUserMapper INSTANCE = Mappers.getMapper(KoreaderUserMapper.class);

    // TODO(grimmory-rename): Remove @Mapping once the entity field and DB column are renamed
    //  from sync_with_booklore_reader to sync_with_grimmory_reader (requires Flyway migration).
    @Mapping(source = "syncWithBookloreReader", target = "syncWithGrimmoryReader")
    KoreaderUser toDto(KoreaderUserEntity entity);

    @Mapping(source = "syncWithGrimmoryReader", target = "syncWithBookloreReader")
    KoreaderUserEntity toEntity(KoreaderUser dto);

    List<KoreaderUser> toDtoList(List<KoreaderUserEntity> entities);
}
