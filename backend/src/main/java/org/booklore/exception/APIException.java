package org.booklore.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class APIException extends RuntimeException {
    private final HttpStatus status;
    private final String message;

    public APIException(String formattedMessage, HttpStatus status) {
        super(formattedMessage);
        this.status = status;
        this.message = formattedMessage;
    }
}
