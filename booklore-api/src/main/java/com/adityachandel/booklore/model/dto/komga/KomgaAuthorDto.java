package com.adityachandel.booklore.model.dto.komga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KomgaAuthorDto {
    private String name;
    private String role;
}
