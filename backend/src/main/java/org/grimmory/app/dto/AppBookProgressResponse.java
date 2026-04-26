package org.grimmory.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppBookProgressResponse {
    private Float readProgress;
    private String readStatus;
    private Instant lastReadTime;
    private AppBookDetail.EpubProgress epubProgress;
    private AppBookDetail.PdfProgress pdfProgress;
    private AppBookDetail.CbxProgress cbxProgress;
    private AppBookDetail.AudiobookProgress audiobookProgress;
    private AppBookDetail.KoreaderProgress koreaderProgress;
}
