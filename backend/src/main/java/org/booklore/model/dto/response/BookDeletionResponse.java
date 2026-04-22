package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookDeletionResponse {
    private Set<Long> deleted;
    private List<Long> failedFileDeletions;
}
