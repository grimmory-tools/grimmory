package org.booklore.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RuleOperator {
    @JsonProperty("equals")
    EQUALS,
    @JsonProperty("not_equals")
    NOT_EQUALS,
    @JsonProperty("contains")
    CONTAINS,
    @JsonProperty("does_not_contain")
    DOES_NOT_CONTAIN,
    @JsonProperty("starts_with")
    STARTS_WITH,
    @JsonProperty("ends_with")
    ENDS_WITH,
    @JsonProperty("greater_than")
    GREATER_THAN,
    @JsonProperty("greater_than_equal_to")
    GREATER_THAN_EQUAL_TO,
    @JsonProperty("less_than")
    LESS_THAN,
    @JsonProperty("less_than_equal_to")
    LESS_THAN_EQUAL_TO,
    @JsonProperty("in_between")
    IN_BETWEEN,
    @JsonProperty("is_empty")
    IS_EMPTY,
    @JsonProperty("is_not_empty")
    IS_NOT_EMPTY,
    @JsonProperty("includes_any")
    INCLUDES_ANY,
    @JsonProperty("excludes_all")
    EXCLUDES_ALL,
    @JsonProperty("includes_all")
    INCLUDES_ALL,
    @JsonProperty("within_last")
    WITHIN_LAST,
    @JsonProperty("older_than")
    OLDER_THAN,
    @JsonProperty("this_period")
    THIS_PERIOD
}
