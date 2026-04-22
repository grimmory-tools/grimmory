package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PdfOutlineItem {
    private String title;
    private Integer pageNumber;
    private List<PdfOutlineItem> children;
}
