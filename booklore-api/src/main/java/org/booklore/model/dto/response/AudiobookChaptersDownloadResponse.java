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
public class AudiobookChaptersDownloadResponse {
    private Long bookId;
    private String downloadUrl;
    private List<AudiobookChapter> chapters;
    private String note;
}
