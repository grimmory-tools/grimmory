package org.booklore.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(OutputCaptureExtension.class)
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
    void handleHttpMessageNotWritableException_recognizesWrappedClientDisconnect(CapturedOutput output) {
        var clientAbort = new ClientAbortException(new IOException("Broken pipe"));
        var ex = new HttpMessageNotWritableException("Could not write JSON",
                new IllegalStateException("Serialization failed", clientAbort));

        assertThat(new GlobalExceptionHandler().handleHttpMessageNotWritableException(ex)).isNull();
        assertThat(output.getOut()).contains("Request was canceled by client while sending the response");
        assertThat(output.getOut()).doesNotContain("Unhandled exception");
    }

    @Test
    void handleHttpMessageNotWritableException_preservesOtherSerializationErrors() {
        var ex = new HttpMessageNotWritableException("Could not write JSON",
                new IllegalStateException("Invalid object"));

        var response = new GlobalExceptionHandler().handleHttpMessageNotWritableException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred.");
    }

    @Test
    void handleHttpMessageNotWritableException_doesNotMistakeUpstreamFailureForClientDisconnect() {
        var ex = new HttpMessageNotWritableException("Could not write JSON",
                new RestClientException("Upstream request failed", new IOException("Broken pipe")));

        var response = new GlobalExceptionHandler().handleHttpMessageNotWritableException(ex);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
    }
}
