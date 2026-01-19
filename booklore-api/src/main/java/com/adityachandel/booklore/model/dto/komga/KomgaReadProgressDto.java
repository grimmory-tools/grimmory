package com.adityachandel.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaReadProgressDto {
    private Integer page;
    private Boolean completed;
    private Instant readDate;
    private Instant created;
    private Instant lastModified;
}
