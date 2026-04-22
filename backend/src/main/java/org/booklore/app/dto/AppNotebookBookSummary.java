package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppNotebookBookSummary {
    private Long bookId;
    private String bookTitle;
    private int noteCount;
    private List<String> authors;
    private Instant coverUpdatedOn;
}
