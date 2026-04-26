package org.grimmory.mapper;

import org.grimmory.model.dto.BookViewerSetting;
import org.grimmory.model.entity.PdfViewerPreferencesEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface BookViewerSettingMapper {

    BookViewerSetting toBookViewerSetting(PdfViewerPreferencesEntity entity);

}
