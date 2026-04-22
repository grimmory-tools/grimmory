package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Rule {
    private String type;
    private RuleField field;
    private RuleOperator operator;
    private Object value;
    private Object valueStart;
    private Object valueEnd;
}
