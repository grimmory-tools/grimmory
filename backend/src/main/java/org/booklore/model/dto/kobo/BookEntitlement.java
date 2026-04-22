package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookEntitlement {
    private ActivePeriod activePeriod;

    @JsonProperty("IsRemoved")
    @Builder.Default
    private Boolean removed = false;

    private String status;

    @Builder.Default
    private String accessibility = "Full";

    private String crossRevisionId;
    private String revisionId;

    @JsonProperty("IsHiddenFromArchive")
    @Builder.Default
    private boolean hiddenFromArchive = false;

    private String id;
    private String created;
    private String lastModified;

    @JsonProperty("IsLocked")
    @Builder.Default
    private boolean locked = false;

    @Builder.Default
    private String originCategory = "Imported";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActivePeriod {
        private String from;
        private String to;
    }
}