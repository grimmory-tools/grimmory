package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EpubManifestItem {
    private String id;
    private String href;
    private String mediaType;
    private List<String> properties;
    private long size;
}
