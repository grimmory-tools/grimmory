package org.booklore.mapper;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.FetchedProposal;
import org.booklore.model.entity.MetadataFetchProposalEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

@Mapper(componentModel = "spring")
@Slf4j
public abstract class FetchedProposalMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    @Mapping(target = "metadataJson", ignore = true)
    @Mapping(target = "taskId", expression = "java(getTaskId(entity))")
    public abstract FetchedProposal toDto(MetadataFetchProposalEntity entity);

    protected String getTaskId(MetadataFetchProposalEntity entity) {
        return entity.getJob() != null ? entity.getJob().getTaskId() : null;
    }

    @AfterMapping
    protected void mapMetadataJson(MetadataFetchProposalEntity entity, @MappingTarget FetchedProposal target) {
        if (entity.getMetadataJson() != null) {
            try {
                BookMetadata metadata = objectMapper.readValue(entity.getMetadataJson(), BookMetadata.class);
                target.setMetadataJson(metadata);
            } catch (Exception e) {
                log.error("Failed to parse metadata JSON for proposal id {}: {}", entity.getProposalId(), e.getMessage(), e);
            }
        }
    }
}