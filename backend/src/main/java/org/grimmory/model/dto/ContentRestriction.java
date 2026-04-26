package org.grimmory.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.grimmory.model.enums.ContentRestrictionMode;
import org.grimmory.model.enums.ContentRestrictionType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentRestriction {
    private Long id;
    private Long userId;
    private ContentRestrictionType restrictionType;
    private ContentRestrictionMode mode;
    private String value;
    private LocalDateTime createdAt;
}
