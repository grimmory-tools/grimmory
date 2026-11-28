package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;
import java.util.List;
import org.booklore.model.enums.ReadStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeriesCoverBook {
    private Long bookId;
    private Instant coverUpdatedOn;
    private Float seriesNumber;
    private String primaryFileType;
    private String title;
    private List<String> authors;
    private Instant audiobookCoverUpdatedOn;
    private ReadStatus readStatus;
}
