package org.booklore.browse;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Link(List<String> rel, String href, String type) {

    public static final String JSON_TYPE = "application/json";

    public static Link json(List<String> rel, String href) {
        return new Link(rel, href, JSON_TYPE);
    }
}
