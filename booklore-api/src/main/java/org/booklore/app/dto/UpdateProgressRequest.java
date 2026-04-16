package org.booklore.app.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.booklore.model.dto.progress.AudiobookProgress;
import org.booklore.model.dto.progress.CbxProgress;
import org.booklore.model.dto.progress.EpubProgress;
import org.booklore.model.dto.progress.PdfProgress;
import org.booklore.model.dto.request.BookFileProgress;

import java.time.Instant;

@Data
public class UpdateProgressRequest {

    private BookFileProgress fileProgress;

    @Deprecated
    private EpubProgress epubProgress;
    @Deprecated
    private PdfProgress pdfProgress;
    @Deprecated
    private CbxProgress cbxProgress;
    @Deprecated
    private AudiobookProgress audiobookProgress;

    private Instant dateFinished;

    @AssertTrue(message = "At least one progress field must be provided")
    public boolean isProgressValid() {
        return fileProgress != null || epubProgress != null || pdfProgress != null || cbxProgress != null || audiobookProgress != null || dateFinished != null;
    }
}
