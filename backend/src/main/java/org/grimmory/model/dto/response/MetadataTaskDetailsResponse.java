package org.booklore.model.dto.response;

import org.booklore.model.dto.MetadataFetchTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataTaskDetailsResponse {

    private MetadataFetchTask task;
}