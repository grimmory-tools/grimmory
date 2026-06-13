package org.booklore.grimmlink.controller;

import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.dto.GrimmlinkCapabilitiesResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/capabilities")
public class GrimmlinkV1CapabilitiesController {

    private static final GrimmlinkCapabilitiesResponse CAPABILITIES =
            new GrimmlinkCapabilitiesResponse(
                    "v1",
                    true,
                    true,
                    true,
                    true,
                    true,
                    true);

    @GetMapping
    public ResponseEntity<GrimmlinkCapabilitiesResponse> getCapabilities() {
        return ResponseEntity.ok(CAPABILITIES);
    }
}
