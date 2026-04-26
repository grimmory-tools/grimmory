package org.grimmory.mapper;

import org.grimmory.model.dto.EmailProviderV2;
import org.grimmory.model.dto.request.CreateEmailProviderRequest;
import org.grimmory.model.entity.EmailProviderV2Entity;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EmailProviderV2Mapper {

    @Mapping(target = "defaultProvider", expression = "java(entity.getId() != null && entity.getId().equals(defaultProviderId))")
    EmailProviderV2 toDTO(EmailProviderV2Entity entity, @Context Long defaultProviderId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "defaultProvider", ignore = true)
    @Mapping(target = "shared", ignore = true)
    EmailProviderV2Entity toEntity(CreateEmailProviderRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "defaultProvider", ignore = true)
    @Mapping(target = "shared", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(CreateEmailProviderRequest request, @MappingTarget EmailProviderV2Entity entity);
}
