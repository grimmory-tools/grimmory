package org.booklore.grimmlink.dto;

import lombok.Data;

@Data
public class GrimmlinkLocationPayload {
    private String pos0;
    private String pos1;
    private Integer pageno;
    private String cfi;
    private String raw;
}
