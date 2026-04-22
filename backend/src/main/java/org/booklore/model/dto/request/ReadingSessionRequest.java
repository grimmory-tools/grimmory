package org.booklore.model.dto.request;

import org.booklore.model.enums.BookFileType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionRequest {
    @NotNull
    private Long bookId;

    private BookFileType bookType;

    @NotNull
    private Instant startTime;

    @NotNull
    private Instant endTime;

    @NotNull
    private Integer durationSeconds;

    private String durationFormatted;

    private Float startProgress;

    private Float endProgress;

    private Float progressDelta;

    private String startLocation;

    private String endLocation;
}
