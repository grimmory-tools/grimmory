package com.adityachandel.booklore.model.dto.settings;

import com.adityachandel.booklore.model.entity.LibraryEntity;
import com.adityachandel.booklore.model.entity.LibraryPathEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.nio.file.Paths;

@Builder
@Data
@AllArgsConstructor
public class LibraryFile {
    private LibraryEntity libraryEntity;
    private LibraryPathEntity libraryPathEntity;
    private String fileSubPath;
    private String fileName;
    private BookFileType bookFileType;

    public Path getFullPath() {
        if (fileSubPath == null || fileSubPath.isEmpty()) {
            return Paths.get(libraryPathEntity.getPath(), fileName);
        }
        return Paths.get(libraryPathEntity.getPath(), fileSubPath, fileName);
    }
}
