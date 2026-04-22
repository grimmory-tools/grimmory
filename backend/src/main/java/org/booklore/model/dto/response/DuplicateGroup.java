package org.booklore.model.dto.response;

import org.booklore.model.dto.Book;

import java.util.List;

public record DuplicateGroup(
        Long suggestedTargetBookId,
        String matchReason,
        List<Book> books
) {}
