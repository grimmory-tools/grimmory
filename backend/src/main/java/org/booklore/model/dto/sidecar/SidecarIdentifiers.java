package org.booklore.model.dto.sidecar;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SidecarIdentifiers {
    private String asin;
    private String goodreadsId;
    private String googleId;
    private String hardcoverId;
    private String hardcoverBookId;
    private String comicvineId;
    private String lubimyczytacId;
    private String ranobedbId;
    private String audibleId;
}
