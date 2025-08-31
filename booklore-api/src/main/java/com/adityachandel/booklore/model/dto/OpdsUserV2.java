package com.adityachandel.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpdsUserV2 {
    private Long id;
    private Long userId;
    private String username;
    @JsonIgnore
    private String passwordHash;
}
