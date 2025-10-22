package com.adityachandel.booklore.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Topic {
    BOOK_ADD("/queue/book-add"),
    BOOK_UPDATE("/queue/book-update"),
    BOOKS_REMOVE("/queue/books-remove"),
    BOOK_METADATA_UPDATE("/queue/book-metadata-update"),
    BOOK_METADATA_BATCH_UPDATE("/queue/book-metadata-batch-update"),
    BOOK_METADATA_BATCH_PROGRESS("/queue/book-metadata-batch-progress"),
    BOOKDROP_FILE("/queue/bookdrop-file"),
    DUPLICATE_FILE("/queue/duplicate-file"),
    LOG("/queue/log"),
    TASK_PROGRESS("/queue/task-progress");

    private final String path;

    @Override
    public String toString() {
        return path;
    }
}