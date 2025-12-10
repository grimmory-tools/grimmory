package com.adityachandel.booklore.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookMarkRequest {
    @NotNull
    private Long bookId;
    @NotEmpty
    private String cfi;
    private String title;
}
