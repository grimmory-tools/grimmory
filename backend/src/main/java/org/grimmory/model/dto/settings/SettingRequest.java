package org.grimmory.model.dto.settings;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SettingRequest {
    private String name;
    private Object value;
}
