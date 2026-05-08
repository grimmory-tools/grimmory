package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccessTokenDto {
    private String accessToken;

    private String refreshToken;

    private Long expires;

    @Builder.Default
    private Boolean isDefaultPassword = null;
}
