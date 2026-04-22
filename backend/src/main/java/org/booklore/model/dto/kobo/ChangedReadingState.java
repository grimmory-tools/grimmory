package org.booklore.model.dto.kobo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class ChangedReadingState implements Entitlement {

    private WrappedReadingState changedReadingState;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    public static class WrappedReadingState {
        private KoboReadingState readingState;
    }
}
