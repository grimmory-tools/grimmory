package org.booklore.model.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Set;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MetadataSelection.Values.class, name = "values"),
        @JsonSubTypes.Type(value = MetadataSelection.Matching.class, name = "matching")
})
public sealed interface MetadataSelection {

    record Values(@NotEmpty List<String> values) implements MetadataSelection {
    }

    record Matching(String search, Set<String> excludedValues) implements MetadataSelection {
    }
}
