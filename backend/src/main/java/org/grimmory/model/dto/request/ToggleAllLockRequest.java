package org.grimmory.model.dto.request;

import org.grimmory.model.enums.Lock;
import lombok.Data;

import java.util.Set;

@Data
public class ToggleAllLockRequest {
    private Set<Long> bookIds;
    private Lock lock;
}
