package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.booklore.model.dto.progress.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Builder
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Book {
    private Long id;
    private Long libraryId;
    private String libraryName;
    private BookFile primaryFile;
    private String title;
    private Instant lastReadTime;
    private Instant addedOn;
    private BookMetadata metadata;
    private Float metadataMatchScore;
    private PdfProgress pdfProgress;
    private EpubProgress epubProgress;
    private CbxProgress cbxProgress;
    private AudiobookProgress audiobookProgress;
    private KoProgress koreaderProgress;
    private KoboProgress koboProgress;
    private Integer personalRating;
    private Set<Shelf> shelves;
    private String readStatus;
    private Instant dateFinished;
    private LibraryPath libraryPath;
    private List<BookFile> alternativeFormats;
    private List<BookFile> supplementaryFiles;
    private Boolean isPhysical;
}
