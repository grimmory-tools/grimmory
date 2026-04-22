package org.booklore.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetadataRefreshRequest {
    @NotNull(message = "Refresh type cannot be null")
    private RefreshType refreshType;
    private Long libraryId;
    private Set<Long> bookIds;
    private MetadataRefreshOptions refreshOptions;

    public enum RefreshType {
        BOOKS, LIBRARY
    }
}
