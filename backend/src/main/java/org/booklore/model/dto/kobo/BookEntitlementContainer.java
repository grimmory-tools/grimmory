package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookEntitlementContainer {
    private BookEntitlement bookEntitlement;
    private KoboBookMetadata bookMetadata;
    private KoboReadingState readingState;
}
