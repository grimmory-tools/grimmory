package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.dto.request.ReadProgressRequest;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Book {
    private Long id;
    private BookFileType bookType;
    private Long libraryId;
    private String fileName;
    private String filePath;
    private Long fileSizeKb;
    private String title;
    private Instant lastReadTime;
    private Instant addedOn;
    private BookMetadata metadata;
    private PdfProgress pdfProgress;
    private EpubProgress epubProgress;
    private List<Shelf> shelves;
}
