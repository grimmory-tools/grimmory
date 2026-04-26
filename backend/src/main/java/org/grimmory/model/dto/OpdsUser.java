package org.grimmory.model.dto;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class OpdsUser {
    private Long id;
    private String username;
    @JsonIgnore
    private String password;
}
