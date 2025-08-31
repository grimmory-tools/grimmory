package com.adityachandel.booklore.model.dto.request;

import lombok.Data;

@Data
public class OpdsUserV2CreateRequest {
    private String username;
    private String password;
}
