package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GrimmlinkShelfSummary {
    private Long id;
    private String name;
    private String type;
    private String visibility;
    private Integer bookCount;
    private String description;
}
