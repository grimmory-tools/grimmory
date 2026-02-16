package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionItemRequest {
    @NotNull(message = "Start time is required")
    private Instant startTime;

    @NotNull(message = "End time is required")
    private Instant endTime;

    @NotNull(message = "Duration is required")
    private Integer durationSeconds;

    private String durationFormatted;

    private Float startProgress;

    private Float endProgress;

    private Float progressDelta;

    @Size(max = 500, message = "Start location must not exceed 500 characters")
    private String startLocation;

    @Size(max = 500, message = "End location must not exceed 500 characters")
    private String endLocation;
}
