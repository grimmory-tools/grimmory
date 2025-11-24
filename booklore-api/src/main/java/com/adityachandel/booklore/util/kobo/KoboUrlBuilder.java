package com.adityachandel.booklore.util.kobo;

import com.adityachandel.booklore.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class KoboUrlBuilder {

    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.\\d+");
    @Value("${server.port}")
    private int serverPort;

    public UriComponentsBuilder baseBuilder() {
        HttpServletRequest request = RequestUtils.getCurrentRequest();

        UriComponentsBuilder builder = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .replacePath("")
                .replaceQuery(null)
                .port(-1);

        String host = builder.build().getHost();

        if (host == null) host = "";

        String xfPort = request.getHeader("X-Forwarded-Port");
        try {
            int port = Integer.parseInt(xfPort);

            if (IP_ADDRESS_PATTERN.matcher(host).matches() || "localhost".equals(host)) {
                builder.port(port);
            }
            log.info("Applied X-Forwarded-Port: {}", port);
        } catch (NumberFormatException e) {
            builder.port(serverPort);
            log.warn("Invalid X-Forwarded-Port header: {}", xfPort);
        }

        log.info("Final base URL: {}", builder.build().toUriString());
        return builder;
    }

    public String downloadUrl(String token, Long bookId) {
        return baseBuilder()
                .pathSegment("api", "kobo", token, "v1", "books", "{bookId}", "download")
                .buildAndExpand(bookId)
                .toUriString();
    }

    public String imageUrlTemplate(String token) {
        return baseBuilder()
                .pathSegment("api", "kobo", token, "v1", "books", "{ImageId}", "thumbnail", "{Width}", "{Height}", "false", "image.jpg")
                .build()
                .toUriString();
    }

    public String imageUrlQualityTemplate(String token) {
        return baseBuilder()
                .pathSegment("api", "kobo", token, "v1", "books", "{ImageId}", "thumbnail", "{Width}", "{Height}", "{Quality}", "{IsGreyscale}", "image.jpg")
                .build()
                .toUriString();
    }
}