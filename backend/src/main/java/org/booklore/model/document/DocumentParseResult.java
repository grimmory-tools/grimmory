package org.booklore.model.document;

import org.booklore.model.enums.DocumentParseStatus;

public record DocumentParseResult(DocumentParseStatus status, DocumentContent content) {

    public static DocumentParseResult readable(DocumentContent content) {
        return new DocumentParseResult(DocumentParseStatus.READABLE, content);
    }

    public static DocumentParseResult unreadable() {
        return new DocumentParseResult(DocumentParseStatus.UNREADABLE, null);
    }

    public static DocumentParseResult indeterminate() {
        return new DocumentParseResult(null, null);
    }

    public boolean isReadable() {
        return status == DocumentParseStatus.READABLE;
    }
}
