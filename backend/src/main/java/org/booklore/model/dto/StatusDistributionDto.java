package org.booklore.model.dto;

import org.booklore.model.enums.ReadStatus;

public interface StatusDistributionDto {
    ReadStatus getStatus();
    Long getCount();
}
