package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotebookBookOption {
    private Long bookId;
    private String bookTitle;
}
