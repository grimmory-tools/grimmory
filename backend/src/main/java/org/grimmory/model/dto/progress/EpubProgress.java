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
public class EpubProgress {
    String cfi;
    String href;
    Float contentSourceProgressPercent;
    @NotNull
    Float percentage;
    String ttsPositionCfi;
}
