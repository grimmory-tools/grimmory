package org.booklore.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.SpaContentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Controller that serves the Angular SPA index.html with the correct base href
 * for subpath deployments (e.g., /grimmory).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class IndexController {

    private final SpaContentProvider spaContentProvider;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> index() {
        String indexHtml = spaContentProvider.getIndexHtml();
        if (indexHtml != null) {
            return ResponseEntity.ok(indexHtml);
        }
        return serveOriginal("static/index.html", MediaType.TEXT_HTML);
    }

    @GetMapping(value = "/index.html", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> indexHtml() {
        return index();
    }

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    @ResponseBody
    public ResponseEntity<String> manifest() {
        String manifest = spaContentProvider.getManifest();
        if (manifest != null) {
            return ResponseEntity.ok(manifest);
        }
        return serveOriginal("static/manifest.webmanifest", MediaType.APPLICATION_JSON);
    }

    @GetMapping(value = "/ngsw.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> ngswJson() {
        String ngswJson = spaContentProvider.getNgswJson();
        if (ngswJson != null) {
            return ResponseEntity.ok(ngswJson);
        }
        return serveOriginal("static/ngsw.json", MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<String> serveOriginal(String resourcePath, MediaType mediaType) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .body(resource.getContentAsString(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            log.warn("Failed to load {}: {}", resourcePath, e.getMessage());
        }
        return ResponseEntity.notFound().build();
    }
}
