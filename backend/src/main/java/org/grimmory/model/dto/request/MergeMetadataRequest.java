package org.grimmory.model.dto.request;

import org.grimmory.model.enums.MergeMetadataType;
import lombok.Data;

import java.util.List;

@Data
public class MergeMetadataRequest {
    private MergeMetadataType metadataType;
    private List<String> targetValues;
    private List<String> valuesToMerge;
}
