package org.booklore.browse;

import org.booklore.exception.APIException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a cursor parameter is missing, malformed, or carries an unsupported version.
 * Maps to HTTP 400.
 */
public class InvalidCursorException extends APIException {

    public InvalidCursorException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
