package org.booklore.mapper;

import org.booklore.model.dto.PdfViewerPreferences;
import org.booklore.model.entity.PdfViewerPreferencesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PdfViewerPreferencesMapper {

    PdfViewerPreferences toModel(PdfViewerPreferencesEntity entity);

    PdfViewerPreferencesEntity toEntity(PdfViewerPreferences model);
}
