package org.grimmory.model.dto.response;

import org.grimmory.model.dto.Book;

public record DetachBookFileResponse(Book sourceBook, Book newBook) {}
