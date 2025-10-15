package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.EmailRecipientV2;
import com.adityachandel.booklore.model.dto.request.CreateEmailRecipientRequest;
import com.adityachandel.booklore.model.entity.EmailRecipientV2Entity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmailRecipientV2Mapper {

    EmailRecipientV2 toDTO(EmailRecipientV2Entity entity);

    EmailRecipientV2Entity toEntity(EmailRecipientV2 emailRecipient);

    EmailRecipientV2Entity toEntity(CreateEmailRecipientRequest createRequest);

    void updateEntityFromRequest(CreateEmailRecipientRequest request, @MappingTarget EmailRecipientV2Entity entity);
}