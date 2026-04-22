package org.booklore.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmailRecipientRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email length must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name length must not exceed 100 characters")
    private String name;

    private boolean defaultRecipient;
}