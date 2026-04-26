package org.grimmory.model.dto.response;

import org.grimmory.model.dto.Book;

import java.util.List;

public record AttachBookFileResponse(Book updatedBook, List<Long> deletedSourceBookIds) {}
