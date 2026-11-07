package org.booklore.util.kobo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.util.RequestUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
@RequiredArgsConstructor
public class KoboUrlBuilder {

    public UriComponentsBuilder baseBuilder() {
        UriComponentsBuilder builder = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .replaceQuery(null);

        UriComponents built = builder.build();
        if (built.getPort() == -1 && !hasForwardedHeaders()) {
            int localPort = RequestUtils.getCurrentRequest().getLocalPort();
            if (!isDefaultPort(built.getScheme(), localPort)) {
                builder.port(localPort);
            }
        }

        log.debug("Final base URL: {}", builder.build().toUriString());
        return builder;
    }

    private boolean hasForwardedHeaders() {
        HttpServletRequest request = RequestUtils.getCurrentRequest();
        return request.getHeader("X-Forwarded-Host") != null
                || request.getHeader("X-Forwarded-Port") != null
                || request.getHeader("X-Forwarded-Proto") != null
                || request.getHeader("Forwarded") != null;
    }

    private boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    }

    public String withBaseUrl(String token, String... pathSegments) {
        UriComponentsBuilder builder = baseBuilder()
                .pathSegment("api", "kobo", token);

        for (String p : pathSegments) {
            builder.pathSegment(p);
        }

        return builder
                .build()
                .toUriString();
    }

    public String downloadUrl(String token, Long bookId) {
        return withBaseUrl(token, "v1", "books", bookId.toString(), "download");
    }

    public String imageUrlTemplate(String token) {
        return withBaseUrl( token, "v1", "books", "{ImageId}", "thumbnail", "{Width}", "{Height}", "false", "image.jpg");
    }

    public String imageUrlQualityTemplate(String token) {
        return withBaseUrl( token,  "v1", "books", "{ImageId}", "thumbnail", "{Width}", "{Height}", "{Quality}", "{IsGreyscale}", "image.jpg");
    }
}
