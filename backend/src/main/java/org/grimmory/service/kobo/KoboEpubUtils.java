package org.booklore.service.kobo;

import lombok.experimental.UtilityClass;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;

import java.net.URI;

@UtilityClass
class KoboEpubUtils {

    static BookFileEntity getSyncedEpubFile(BookEntity book) {
        BookFileEntity primaryFile = book != null ? book.getPrimaryBookFile() : null;
        if (primaryFile == null || primaryFile.getBookType() != BookFileType.EPUB) {
            return null;
        }
        return primaryFile;
    }

    static String decodeHrefPath(String href) {
        if (href == null) {
            return null;
        }

        String normalizedPath = href.replaceFirst("#.*$", "")
                .replace('\\', '/');
        try {
            String decodedPath = URI.create(normalizedPath).getPath();
            return decodedPath != null ? decodedPath : normalizedPath;
        } catch (IllegalArgumentException e) {
            return normalizedPath;
        }
    }

    static String normalizeHref(String href) {
        String decodedHref = decodeHrefPath(href);
        if (decodedHref == null) {
            return null;
        }

        return decodedHref.replaceFirst("^/+", "");
    }

}
