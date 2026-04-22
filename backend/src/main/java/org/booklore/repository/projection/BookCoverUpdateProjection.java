package org.booklore.repository.projection;

import java.time.Instant;

public interface BookCoverUpdateProjection {
    Long getId();
    Instant getCoverUpdatedOn();
}

