package org.booklore.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Topic {
    BOOK_ADD("/queue/book-add"),
    BOOK_UPDATE("/queue/book-update"),
    BOOKS_COVER_UPDATE("/queue/books-cover-update"),
    BOOKS_REMOVE("/queue/books-remove"),
    BOOK_METADATA_UPDATE("/queue/book-metadata-update"),
    BOOK_METADATA_BATCH_UPDATE("/queue/book-metadata-batch-update"),
    BOOK_METADATA_BATCH_PROGRESS("/queue/book-metadata-batch-progress"),
    BOOKDROP_FILE("/queue/bookdrop-file"),
    LOG("/queue/log"),
    TASK_PROGRESS("/queue/task-progress"),
    LIBRARY_HEALTH("/topic/library-health"),
    SESSION_REVOKED("/queue/session-revoked");

    private final String path;

    @Override
    public String toString() {
        return path;
    }
}