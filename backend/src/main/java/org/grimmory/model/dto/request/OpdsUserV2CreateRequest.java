package org.grimmory.model.dto.request;

import org.grimmory.model.enums.OpdsSortOrder;
import lombok.Data;

@Data
public class OpdsUserV2CreateRequest {
    private String username;
    private String password;
    private OpdsSortOrder sortOrder;
}
