package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningFinishFunnelResponse {
    private Long totalStarted;
    private Long reached25;
    private Long reached50;
    private Long reached75;
    private Long completed;
}
