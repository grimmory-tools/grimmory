package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.dto.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppBookContextResponse {
    private AppBookDetail book;
    private PdfViewerPreferences pdfSettings;
    private NewPdfViewerPreferences newPdfSettings;
    private EbookViewerPreferences ebookSettings;
    private CbxViewerPreferences cbxSettings;
}
