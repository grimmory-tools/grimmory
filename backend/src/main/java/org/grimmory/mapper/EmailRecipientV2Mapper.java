package org.grimmory.mapper;

import org.grimmory.model.dto.EmailRecipientV2;
import org.grimmory.model.dto.request.CreateEmailRecipientRequest;
import org.grimmory.model.entity.EmailRecipientV2Entity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EmailRecipientV2Mapper {

    EmailRecipientV2 toDTO(EmailRecipientV2Entity entity);

    EmailRecipientV2Entity toEntity(EmailRecipientV2 emailRecipient);

    EmailRecipientV2Entity toEntity(CreateEmailRecipientRequest createRequest);

    void updateEntityFromRequest(CreateEmailRecipientRequest request, @MappingTarget EmailRecipientV2Entity entity);
}