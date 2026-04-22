package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum JoinType {
    @JsonProperty("and")
    AND,
    @JsonProperty("or")
    OR
}

