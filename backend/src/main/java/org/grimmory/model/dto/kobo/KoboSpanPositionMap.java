package org.booklore.model.dto.kobo;

import java.util.List;

public record KoboSpanPositionMap(List<Chapter> chapters) {

    public KoboSpanPositionMap {
        chapters = chapters == null ? List.of() : List.copyOf(chapters);
    }

    public record Chapter(String sourceHref,
                          String normalizedHref,
                          int spineIndex,
                          float globalStartProgress,
                          float globalEndProgress,
                          List<Span> spans) {

        public Chapter {
            spans = spans == null ? List.of() : List.copyOf(spans);
        }
    }

    public record Span(String id, float progression) {
    }
}
