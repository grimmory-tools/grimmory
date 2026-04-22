package org.booklore.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoverFetchRequest {
    private String isbn;
    private String title;
    private String author;
    private String coverType; // "ebook" or "audiobook"
}
