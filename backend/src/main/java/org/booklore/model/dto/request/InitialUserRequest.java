package org.booklore.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InitialUserRequest {
    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String name;

    @NotBlank
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters long")
    private String password;
}
