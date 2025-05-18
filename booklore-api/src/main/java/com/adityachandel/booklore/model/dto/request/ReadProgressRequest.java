package com.adityachandel.booklore.model.dto.request;

import com.adityachandel.booklore.model.dto.EpubProgress;
import com.adityachandel.booklore.model.dto.PdfProgress;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReadProgressRequest {
    @NotNull
    private Long bookId;
    private EpubProgress epubProgress;
    private PdfProgress pdfProgress;

    @AssertTrue(message = "Either epubProgress or pdfProgress must be provided")
    public boolean isProgressValid() {
        return epubProgress != null || pdfProgress != null;
    }
}
