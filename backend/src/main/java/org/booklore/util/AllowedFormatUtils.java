package org.booklore.util;

import lombok.experimental.UtilityClass;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.enums.BookFileExtension;
import org.booklore.model.enums.BookFileType;

import java.util.List;
import java.util.Optional;

@UtilityClass
public class AllowedFormatUtils {

    public Optional<BookFileType> resolveBookFileType(String fileName) {
        return BookFileExtension.fromFileName(fileName)
                .map(BookFileExtension::getType);
    }

    public boolean isAllowed(LibraryEntity library, BookFileType fileType) {
        List<BookFileType> allowedFormats = library.getAllowedFormats();
        return allowedFormats == null || allowedFormats.isEmpty() || allowedFormats.contains(fileType);
    }

    public boolean isAllowedBookFile(LibraryEntity library, String fileName) {
        return resolveBookFileType(fileName)
                .filter(fileType -> isAllowed(library, fileType))
                .isPresent();
    }
}
