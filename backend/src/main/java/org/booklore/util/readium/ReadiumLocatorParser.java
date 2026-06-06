package org.booklore.util.readium;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Slf4j
@UtilityClass
public class ReadiumLocatorParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record ParsedLocator(
            String href,
            Float progression,
            Float totalProgression,
            String cfiFragment,
            String textBefore,
            String textHighlight,
            String textAfter
    ) {}

    public Optional<ParsedLocator> parse(String locatorJson) {
        if (locatorJson == null || locatorJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(locatorJson);

            String href = nullableText(root.path("href"));

            JsonNode locations = root.path("locations");
            Float progression = floatOrNull(locations.path("progression"));
            Float totalProgression = floatOrNull(locations.path("totalProgression"));

            String cfiFragment = null;
            JsonNode fragments = locations.path("fragments");
            if (fragments.isArray() && !fragments.isEmpty()) {
                String frag = fragments.get(0).stringValue();
                if (frag != null && frag.startsWith("epubcfi(")) {
                    cfiFragment = frag;
                }
            }

            JsonNode text = root.path("text");
            String textBefore = nullableText(text.path("before"));
            String textHighlight = nullableText(text.path("highlight"));
            String textAfter = nullableText(text.path("after"));

            return Optional.of(new ParsedLocator(href, progression, totalProgression, cfiFragment,
                    textBefore, textHighlight, textAfter));
        } catch (Exception e) {
            log.warn("Failed to parse Readium Locator JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String nullableText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String text = node.stringValue();
        return (text == null || text.isBlank()) ? null : text;
    }

    private Float floatOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        return (float) node.asDouble();
    }
}
