package org.booklore.grimmlink.controller;

import lombok.RequiredArgsConstructor;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.service.GrimmlinkAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX)
public class GrimmlinkV1AuthController {

    private final GrimmlinkAuthService authService;

    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authorize() {
        return ResponseEntity.ok(authService.authorize());
    }
}
