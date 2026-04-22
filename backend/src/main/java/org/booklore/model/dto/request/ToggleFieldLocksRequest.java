package org.booklore.model.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ToggleFieldLocksRequest {
    private List<Long> bookIds;
    private Map<String, String> fieldActions;
}
