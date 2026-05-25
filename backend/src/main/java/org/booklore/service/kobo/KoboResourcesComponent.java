package org.booklore.service.kobo;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@AllArgsConstructor
@Component
public class KoboResourcesComponent {

    private final ObjectMapper objectMapper;

    private String getFileFromClassPath(String filename) {
        String path = "kobo/" + filename;

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assert is != null;

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Unable to load initialization resource", e);
            return "{}";
        }
    }

    public ObjectNode getResources() throws JacksonException {
        return (ObjectNode) objectMapper.readTree(getFileFromClassPath("resources.json"));
    }
}
