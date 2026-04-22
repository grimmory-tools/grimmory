package org.booklore.model.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KoreaderUser {
    private Long id;
    private String username;
    private String password;
    private String passwordMD5;
    private boolean syncEnabled;
    private boolean syncWithBookloreReader;
}
