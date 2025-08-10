package com.adityachandel.booklore.model.websocket;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum Topic {
    BOOK_ADD("/topic/book-add"),
    BOOKS_REMOVE("/topic/books-remove"),
    BOOK_METADATA_UPDATE("/topic/book-metadata-update"),
    BOOK_METADATA_BATCH_UPDATE("/topic/book-metadata-batch-update"),
    BOOK_METADATA_BATCH_PROGRESS("/topic/book-metadata-batch-progress"),
    BOOKDROP_FILE("/topic/bookdrop-file"),
    TASK("/topic/task"),
    LOG("/topic/log");

    private final String path;

    @Override
    public String toString() {
        return path;
    }
}