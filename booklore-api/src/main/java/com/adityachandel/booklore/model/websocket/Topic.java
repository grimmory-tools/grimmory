package com.adityachandel.booklore.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Topic {
    BOOK_ADD("/queue/book-add"),
    BOOKS_REMOVE("/queue/books-remove"),
    BOOK_METADATA_UPDATE("/queue/book-metadata-update"),
    BOOK_METADATA_BATCH_UPDATE("/queue/book-metadata-batch-update"),
    BOOK_METADATA_BATCH_PROGRESS("/queue/book-metadata-batch-progress"),
    BOOKDROP_FILE("/queue/bookdrop-file"),
    DUPLICATE_FILE("/queue/duplicate-file"),
    TASK("/queue/task"),
    LOG("/queue/log");

    private final String path;

    @Override
    public String toString() {
        return path;
    }
}