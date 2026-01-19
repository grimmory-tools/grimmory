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
public class KomgaMediaDto {
    private String status;
    private String mediaType;
    private String mediaProfile;
    private Integer pagesCount;
    private String comment;
    private Boolean epubDivinaCompatible;
    private Boolean epubIsKepub;
}
