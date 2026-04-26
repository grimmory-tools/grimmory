package org.grimmory.mapper;

import org.grimmory.model.dto.PdfViewerPreferences;
import org.grimmory.model.entity.PdfViewerPreferencesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PdfViewerPreferencesMapper {

    PdfViewerPreferences toModel(PdfViewerPreferencesEntity entity);

    PdfViewerPreferencesEntity toEntity(PdfViewerPreferences model);
}
