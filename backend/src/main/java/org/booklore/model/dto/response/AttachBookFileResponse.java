package org.booklore.model.dto.response;

import org.booklore.model.dto.Book;

import java.util.List;

public record AttachBookFileResponse(Book updatedBook, List<Long> deletedSourceBookIds) {}
