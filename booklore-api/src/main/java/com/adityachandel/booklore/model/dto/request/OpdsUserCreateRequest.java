package com.adityachandel.booklore.model.dto.request;

import lombok.Data;

@Data
public class OpdsUserCreateRequest {
    private String username;
    private String password;
}
