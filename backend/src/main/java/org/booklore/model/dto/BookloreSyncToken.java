package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class BookloreSyncToken {
    private String ongoingSyncPointId;
    private String lastSuccessfulSyncPointId;
    private String rawKoboSyncToken;
}
