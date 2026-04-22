package org.booklore.model.dto;

import org.booklore.model.enums.OpdsSortOrder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpdsUserV2 {
    private Long id;
    private Long userId;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private OpdsSortOrder sortOrder;
}
