package org.booklore.model.dto.komga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KomgaCollectionDto {
    private String id;
    private String name;
    private Boolean ordered;
    private Integer seriesCount;
    private String createdDate;
    private String lastModifiedDate;
}
