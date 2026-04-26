package org.booklore.model.dto.response;

import org.booklore.model.enums.BookFileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionResponse {
    private Long id;
    private Long bookId;
    private String bookTitle;
    private BookFileType bookType;
    private Instant startTime;
    private Instant endTime;
    private Integer durationSeconds;
    private Float startProgress;
    private Float endProgress;
    private Float progressDelta;
    private String startLocation;
    private String endLocation;
    private LocalDateTime createdAt;
}

