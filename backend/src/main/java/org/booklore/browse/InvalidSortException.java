package org.booklore.browse;

import org.booklore.exception.APIException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a sort parameter references a key that is not registered for the target
 * resource. Maps to HTTP 400.
 */
public class InvalidSortException extends APIException {

    public InvalidSortException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
