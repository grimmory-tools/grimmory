package org.booklore.mapper;

import org.booklore.model.dto.BookFile;
import org.booklore.model.entity.BookFileEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AdditionalFileMapper {

    @Mapping(source = "book.id", target = "bookId")
    @Mapping(source = ".", target = "filePath", qualifiedByName = "mapFilePath")
    @Mapping(source = "bookFormat", target = "isBook")
    @Mapping(source = ".", target = "extension", qualifiedByName = "mapExtension")
    BookFile toAdditionalFile(BookFileEntity entity);

    List<BookFile> toAdditionalFiles(List<BookFileEntity> entities);

    @Named("mapFilePath")
    default String mapFilePath(BookFileEntity entity) {
        if (entity == null) return null;
        try {
            return entity.getFullFilePath().toString();
        } catch (Exception e) {
            return null;
        }
    }

    @Named("mapExtension")
    default String mapExtension(BookFileEntity entity) {
        if (entity == null) return null;
        String fileName;
        if (entity.isFolderBased()) {
            var firstFile = entity.getFirstAudioFile();
            fileName = firstFile != null ? firstFile.getFileName().toString() : null;
        } else {
            fileName = entity.getFileName();
        }
        if (fileName == null) return null;
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1).toLowerCase() : null;
    }
}