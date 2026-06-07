package org.booklore.grimmlink.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class GrimmlinkReadingSessionItemRequest {
    @NotNull
    private Instant startTime;
    @NotNull
    private Instant endTime;
    @NotNull
    private Integer durationSeconds;
    @Size(max = 50)
    private String durationFormatted;
    private Float startProgress;
    private Float endProgress;
    private Float progressDelta;
    @Size(max = 500)
    private String startLocation;
    @Size(max = 500)
    private String endLocation;
    private Integer currentPage;
    private Integer totalPages;
}
