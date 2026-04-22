package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookViewerSettings {
    private PdfViewerPreferences pdfSettings;
    private NewPdfViewerPreferences newPdfSettings;
    private EbookViewerPreferences ebookSettings;
    private CbxViewerPreferences cbxSettings;
}