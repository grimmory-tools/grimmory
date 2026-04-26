package org.grimmory.mapper;

import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.dto.EpubMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EpubMetadataMapper {

    EpubMetadataMapper INSTANCE = Mappers.getMapper(EpubMetadataMapper.class);

    EpubMetadata toEpubMetadata(BookMetadata bookMetadata);

    BookMetadata toBookMetadata(EpubMetadata epubMetadata);
}
