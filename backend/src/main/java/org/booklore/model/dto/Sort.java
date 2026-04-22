package org.booklore.model.dto;

import org.booklore.model.enums.SortDirection;
import lombok.Data;

@Data
public class Sort {
    private String field;
    private SortDirection direction;
}
