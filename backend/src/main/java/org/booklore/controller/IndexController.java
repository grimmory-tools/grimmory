package org.booklore.controller;

import lombok.RequiredArgsConstructor;
import org.booklore.config.SpaContentProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the same prepared SPA resources for root requests and client-side routing. */
@RestController
@RequiredArgsConstructor
public class IndexController {

    private final SpaContentProvider spaContentProvider;

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        return serve(spaContentProvider.getIndexHtmlResource(), MediaType.TEXT_HTML);
    }

    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public ResponseEntity<Resource> manifest() {
        return serve(spaContentProvider.getManifestResource(), MediaType.parseMediaType("application/manifest+json"));
    }

    @GetMapping(value = "/ngsw.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> ngswJson() {
        return serve(spaContentProvider.getNgswResource(), MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<Resource> serve(Resource resource, MediaType mediaType) {
        if (!resource.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().noTransform())
                .contentType(mediaType)
                .body(resource);
    }
}
