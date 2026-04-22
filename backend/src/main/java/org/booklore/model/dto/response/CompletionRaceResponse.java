package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionRaceResponse {
    private Long bookId;
    private String bookTitle;
    private Instant sessionDate;
    private Float endProgress;
}
