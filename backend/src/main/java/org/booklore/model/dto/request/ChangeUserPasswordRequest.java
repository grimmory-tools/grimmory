package org.booklore.model.dto.request;

import lombok.Data;

@Data
public class ChangeUserPasswordRequest {
    private Long userId;
    private String newPassword;
}
