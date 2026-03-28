package org.booklore.service.kobo;

import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.enums.BookFileType;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

final class KoboEpubUtils {

    private KoboEpubUtils() {
    }

    static BookFileEntity getSyncedEpubFile(BookEntity book) {
        BookFileEntity primaryFile = book != null ? book.getPrimaryBookFile() : null;
        if (primaryFile == null || primaryFile.getBookType() != BookFileType.EPUB) {
            return null;
        }
        return primaryFile;
    }

    static String normalizeHref(String href) {
        return URLDecoder.decode(href, StandardCharsets.UTF_8)
                .replace('\\', '/')
                .replaceFirst("#.*$", "")
                .replaceFirst("^/+", "");
    }

    static float clampUnit(float value) {
        return Math.max(0f, Math.min(value, 1f));
    }

    static Float clampPercent(Float value) {
        return Math.max(0f, Math.min(value, 100f));
    }
}
