package org.booklore.model.dto.kobo;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KoboHeaders {

    public static final String X_KOBO_SYNCTOKEN = "x-kobo-synctoken";
    public static final String X_KOBO_SYNC = "X-Kobo-sync";
}
