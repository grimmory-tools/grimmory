package org.booklore.model.enums;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public enum KoboReadStatus {
    @JsonProperty("ReadyToRead")
    READY_TO_READ,

    @JsonProperty("Finished")
    FINISHED,

    @JsonProperty("Reading")
    READING,
}
