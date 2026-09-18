package org.booklore.exception;

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
}
