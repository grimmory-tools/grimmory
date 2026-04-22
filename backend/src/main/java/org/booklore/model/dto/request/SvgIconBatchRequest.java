package org.booklore.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SvgIconBatchRequest {

    @NotEmpty(message = "Icons list cannot be empty")
    @Valid
    private List<SvgIconCreateRequest> icons;
}

