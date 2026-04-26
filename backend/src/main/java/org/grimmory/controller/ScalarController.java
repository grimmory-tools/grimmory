package org.grimmory.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "app.api-docs.enabled", havingValue = "true", matchIfMissing = false)
public class ScalarController {

    @GetMapping("/api/docs")
    public String scalar() {
        return "forward:/scalar.html";
    }
}
