package org.booklore.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.service.kobo.KoboServerProxy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/UserStorage")
public class UserStorageController {

    private final KoboServerProxy koboServerProxy;

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<byte[]> proxyUserStorage(HttpServletRequest request) {
        byte[] body = readBody(request);
        return koboServerProxy.proxyToReadingServices(body);
    }

    private byte[] readBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (Exception e) {
            log.warn("Could not read UserStorage request body", e);
            return new byte[0];
        }
    }
}
