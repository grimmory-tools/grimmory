package org.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.enums.BookFileType;

import java.nio.file.Path;
import java.nio.file.Paths;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LibraryFile {
    private LibraryEntity libraryEntity;
    private LibraryPathEntity libraryPathEntity;
    private String fileSubPath;
    private String fileName;
    private BookFileType bookFileType;
    @Builder.Default
    private boolean folderBased = false;

    public Path getFullPath() {
        if (fileSubPath == null || fileSubPath.isEmpty()) {
            return Paths.get(libraryPathEntity.getPath(), fileName);
        }
        return Paths.get(libraryPathEntity.getPath(), fileSubPath, fileName);
    }
}
