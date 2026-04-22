package org.booklore.model.dto.progress;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CbxProgress {
    @NotNull
    Integer page;
    @NotNull
    Float percentage;
}
