package org.grimmory.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbxPageDimension {
    private int pageNumber;
    private int width;
    private int height;
    private boolean wide;
}
