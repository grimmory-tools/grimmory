package org.booklore.model.dto.response;

import org.booklore.model.enums.ReadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookStatusUpdateResponse {
    private Long bookId;
    private ReadStatus readStatus;
    private Instant readStatusModifiedTime;
    private Instant dateFinished;
}

