package org.booklore.model.dto.progress;

import lombok.*;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class KoreaderProgress {
    private Long timestamp;
    private String document;
    private Float percentage;
    private String progress;
    private String device;
    private String device_id;
}
