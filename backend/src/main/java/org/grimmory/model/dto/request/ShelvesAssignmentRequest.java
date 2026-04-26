package org.grimmory.model.dto.request;

import lombok.Data;

import java.util.Set;

@Data
public class ShelvesAssignmentRequest {
    private Set<Long> bookIds;
    private Set<Long> shelvesToAssign;
    private Set<Long> shelvesToUnassign;
}
