package org.booklore.model.dto.request;

import lombok.Data;

@Data
public class HighlightPayload {
    private Long bookId;
    private String text;
    private String note;
    private String cfi;
    private String color;
    private String chapterTitle;
}