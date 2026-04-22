package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendBookByEmailRequest {

    @NotNull(message = "Book ID cannot be null")
    private Long bookId;

    @NotNull(message = "Provider ID cannot be null")
    private Long providerId;

    @NotNull(message = "Recipient ID cannot be null")
    private Long recipientId;

    private Long bookFileId;  // Optional: if null, uses primary file
}
