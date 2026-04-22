package org.booklore.model.dto.response;

import org.booklore.model.dto.Book;

public record DetachBookFileResponse(Book sourceBook, Book newBook) {}
