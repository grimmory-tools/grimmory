package org.grimmory.service.kobo;

import lombok.experimental.UtilityClass;
import org.grimmory.model.entity.BookEntity;
import org.grimmory.model.entity.BookFileEntity;
import org.grimmory.model.enums.BookFileType;

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
