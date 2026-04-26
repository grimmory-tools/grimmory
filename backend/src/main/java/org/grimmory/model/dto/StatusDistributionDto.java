package org.grimmory.model.dto;

import org.grimmory.model.enums.ReadStatus;

public interface StatusDistributionDto {
    ReadStatus getStatus();
    Long getCount();
}
