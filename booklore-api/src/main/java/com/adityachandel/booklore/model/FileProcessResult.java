package com.adityachandel.booklore.model;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.enums.FileProcessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@ToString
@AllArgsConstructor
public class FileProcessResult {
    private final Book book;
    private final FileProcessStatus status;
    @Builder.Default
    private final DuplicateFileInfo duplicate = null;
}
