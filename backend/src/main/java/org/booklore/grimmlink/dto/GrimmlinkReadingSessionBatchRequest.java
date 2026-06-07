package org.booklore.grimmlink.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GrimmlinkReadingSessionBatchRequest {
    @NotNull
    private Long bookId;
    @Size(max = 128)
    private String bookHash;
    @Size(max = 32)
    private String bookType;
    @Size(max = 100)
    private String device;
    @JsonAlias("device_id")
    @Size(max = 255)
    private String deviceId;
    @NotEmpty
    @Size(max = 500)
    @Valid
    private List<GrimmlinkReadingSessionItemRequest> sessions;
}
