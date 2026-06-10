package org.booklore.browse;

import org.booklore.exception.APIException;
import org.springframework.http.HttpStatus;

/**
 * Raised when a request supplies a cursor together with facet/query parameters that differ
 * from the ones the cursor was created with. Rejecting this avoids silently returning an
 * inconsistent page. Maps to HTTP 400.
 */
public class CursorMismatchException extends APIException {

    public CursorMismatchException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
