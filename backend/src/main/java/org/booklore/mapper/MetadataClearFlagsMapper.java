package org.booklore.mapper;

import org.booklore.model.MetadataClearFlags;
import org.booklore.model.dto.request.BulkMetadataUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MetadataClearFlagsMapper {

    @Mapping(target = "publisher", source = "clearPublisher")
    @Mapping(target = "publishedDate", source = "clearPublishedDate")
    @Mapping(target = "seriesName", source = "clearSeriesName")
    @Mapping(target = "seriesTotal", source = "clearSeriesTotal")
    @Mapping(target = "language", source = "clearLanguage")
    @Mapping(target = "authors", source = "clearAuthors")
    @Mapping(target = "categories", source = "clearGenres")
    @Mapping(target = "moods", source = "clearMoods")
    @Mapping(target = "tags", source = "clearTags")
    @Mapping(target = "ageRating", source = "clearAgeRating")
    @Mapping(target = "contentRating", source = "clearContentRating")
    MetadataClearFlags toClearFlags(BulkMetadataUpdateRequest request);
}
