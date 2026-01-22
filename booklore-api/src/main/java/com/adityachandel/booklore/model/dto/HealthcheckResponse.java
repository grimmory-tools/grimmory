package com.adityachandel.booklore.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HealthcheckResponse {
    private String status;
    private String message;
    private LocalDateTime timestamp;
    private String version;
}
