package com.adityachandel.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRecipientV2 {
    private Long id;
    private Long userId;
    private String email;
    private String name;
    private boolean defaultRecipient;
}
