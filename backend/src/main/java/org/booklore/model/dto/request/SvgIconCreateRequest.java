package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SvgIconCreateRequest {
    @NotBlank(message = "SVG name is required")
    @Size(min = 1, max = 255, message = "SVG name must be between 1 and 255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9-]+$", message = "SVG name can only contain alphanumeric characters and hyphens")
    private String svgName;

    @NotBlank(message = "SVG data is required")
    @Size(max = 1048576, message = "SVG data must not exceed 1MB")
    private String svgData;
}
