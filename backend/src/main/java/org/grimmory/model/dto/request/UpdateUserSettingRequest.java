package org.grimmory.model.dto.request;

import lombok.Data;

@Data
public class UpdateUserSettingRequest {
    private String key;
    private Object value;
}