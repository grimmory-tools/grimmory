package org.booklore.grimmlink.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GrimmlinkMetadataSyncResults {
    private GrimmlinkItemResult rating;
    private List<GrimmlinkItemResult> annotations;
    private List<GrimmlinkItemResult> bookmarks;
}
