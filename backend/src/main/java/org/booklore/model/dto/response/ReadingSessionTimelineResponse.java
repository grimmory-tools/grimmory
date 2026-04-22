package org.booklore.model.dto.response;

import org.booklore.model.enums.BookFileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionTimelineResponse {
    private Long bookId;
    private String bookTitle;
    private BookFileType bookType;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Long totalSessions;
    private Long totalDurationSeconds;
}
