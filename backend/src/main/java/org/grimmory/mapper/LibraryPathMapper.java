package org.grimmory.mapper;

import org.grimmory.model.dto.LibraryPath;
import org.grimmory.model.entity.LibraryPathEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LibraryPathMapper {

    LibraryPath toLibraryPath(LibraryPathEntity libraryPathEntity);
}
