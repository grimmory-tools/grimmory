package org.booklore.model.dto.acquisition;

public record ConnectionTestResult(
        boolean success,
        String message
) {
}
