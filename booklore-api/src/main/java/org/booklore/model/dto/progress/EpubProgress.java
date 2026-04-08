package org.booklore.model.dto.progress;

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
    Float percentage;
    String ttsPositionCfi;
}
