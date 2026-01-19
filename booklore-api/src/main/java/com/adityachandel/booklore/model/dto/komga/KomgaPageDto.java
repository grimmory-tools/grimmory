package com.adityachandel.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaPageDto {
    private Integer number;
    private String fileName;
    private String mediaType;
    private Integer width;
    private Integer height;
    private Long fileSize;
}
