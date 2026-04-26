package org.grimmory.app.dto;

import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.grimmory.model.dto.progress.AudiobookProgress;
import org.grimmory.model.dto.progress.CbxProgress;
import org.grimmory.model.dto.progress.EpubProgress;
import org.grimmory.model.dto.progress.PdfProgress;
import org.grimmory.model.dto.request.BookFileProgress;

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
