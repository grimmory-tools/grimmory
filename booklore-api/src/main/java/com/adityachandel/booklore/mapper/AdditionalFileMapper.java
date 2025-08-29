package com.adityachandel.booklore.mapper;

import com.adityachandel.booklore.model.dto.AdditionalFile;
import com.adityachandel.booklore.model.entity.BookAdditionalFileEntity;
import com.adityachandel.booklore.util.FileUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdditionalFileMapper {

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = ".", target = "filePath", qualifiedByName = "mapFilePath")
    AdditionalFile toAdditionalFile(BookAdditionalFileEntity entity);

    List<AdditionalFile> toAdditionalFiles(List<BookAdditionalFileEntity> entities);

    @Named("mapFilePath")
    default String mapFilePath(BookAdditionalFileEntity entity) {
        if (entity == null) return null;
        try {
            return entity.getFullFilePath().toString();
        } catch (Exception e) {
            return null;
        }
    }
}