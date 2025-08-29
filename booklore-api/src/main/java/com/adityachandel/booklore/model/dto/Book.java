package com.adityachandel.booklore.model.dto;

import com.adityachandel.booklore.model.dto.progress.*;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Book {
    private Long id;
    private BookFileType bookType;
    private Long libraryId;
    private String fileName;
    private String filePath;
    private String fileSubPath;
    private Long fileSizeKb;
    private String title;
    private Instant lastReadTime;
    private Instant addedOn;
    private BookMetadata metadata;
    private Float metadataMatchScore;
    private PdfProgress pdfProgress;
    private EpubProgress epubProgress;
    private CbxProgress cbxProgress;
    private KoProgress koreaderProgress;
    private Set<Shelf> shelves;
    private String readStatus;
    private Instant dateFinished;
    private LibraryPath libraryPath;
    private List<AdditionalFile> alternativeFormats;
    private List<AdditionalFile> supplementaryFiles;
}
