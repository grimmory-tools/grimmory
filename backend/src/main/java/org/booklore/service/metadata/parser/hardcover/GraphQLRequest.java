package org.booklore.service.metadata.parser.hardcover;

import lombok.Data;

import java.util.Map;

@Data
public class GraphQLRequest {
    private String query;
    private String operationName;
    private Map<String, Object> variables;
}
