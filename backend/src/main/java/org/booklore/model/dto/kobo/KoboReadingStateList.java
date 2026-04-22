package org.booklore.model.dto.kobo;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.ArrayList;
import java.util.Collection;

@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KoboReadingStateList extends ArrayList<KoboReadingState> {
    public KoboReadingStateList() {
        super();
    }

    public KoboReadingStateList(Collection<KoboReadingState> states) {
        super(states);
    }
}
