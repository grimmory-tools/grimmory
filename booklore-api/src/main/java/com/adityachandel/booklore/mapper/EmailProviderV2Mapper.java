package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.EmailProviderV2;
import com.adityachandel.booklore.model.dto.request.CreateEmailProviderRequest;
import com.adityachandel.booklore.model.entity.EmailProviderV2Entity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmailProviderV2Mapper {

    EmailProviderV2 toDTO(EmailProviderV2Entity entity);
    EmailProviderV2Entity toEntity(EmailProviderV2 emailProvider);
    EmailProviderV2Entity toEntity(CreateEmailProviderRequest createRequest);
    void updateEntityFromRequest(CreateEmailProviderRequest request, @MappingTarget EmailProviderV2Entity entity);
}
