package com.adityachandel.booklore.model.dto;


import lombok.Data;

@Data
public class KoboSyncSettings {
    private Long id;
    private String userId;
    private String token;
    private boolean syncEnabled;
}
