package org.booklore.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class GlobalExceptionHandlerTest {
    @Test
    void handleGenericException_shouldUseSpringErrorResponse() {
        var ex = new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_OCTET_STREAM));
        var handler = new GlobalExceptionHandler();
        var response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(406);
        assertThat(response.getBody().getMessage()).isEqualTo("Acceptable representations: [application/octet-stream].");
    }

    @Test
    void handleEntityNotFoundException_shouldReturnNotFound() {
        var ex = new EntityNotFoundException("Annotation not found: 42");
        var handler = new GlobalExceptionHandler();
        var response = handler.handleEntityNotFoundException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Annotation not found: 42");
    }
}
