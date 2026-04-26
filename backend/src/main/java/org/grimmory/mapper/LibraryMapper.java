package org.grimmory.mapper;

import org.grimmory.model.dto.Library;
import org.grimmory.model.entity.LibraryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LibraryMapper {

    @Mapping(target = "paths", source = "libraryPaths")
    Library toLibrary(LibraryEntity libraryEntity);
}
