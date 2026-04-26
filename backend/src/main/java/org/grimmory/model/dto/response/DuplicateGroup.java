package org.grimmory.model.dto.response;

import org.grimmory.model.dto.Book;

import java.util.List;

public record DuplicateGroup(
        Long suggestedTargetBookId,
        String matchReason,
        List<Book> books
) {}
