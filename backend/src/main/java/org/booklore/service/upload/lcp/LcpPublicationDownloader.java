package org.booklore.service.upload.lcp;

import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LcpPublicationDownloader {
    private final RestClient client;

    public LcpPublicationDownloader(@Qualifier("lcpRestClient") RestClient client) { this.client = client; }

    static void validateUri(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw ApiError.INVALID_INPUT.createException("LCP publication links must use HTTPS on the standard port.");
        }
    }

    public void download(URI initial, Path output, long limit) {
        URI uri = initial;
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                validateUri(uri);
                URI next = client.get().uri(uri).exchange((request, response) -> {
                    if (response.getStatusCode().is3xxRedirection()) {
                        URI location = response.getHeaders().getLocation();
                        if (location == null) throw ApiError.INVALID_INPUT.createException("Invalid LCP download redirect.");
                        return request.getURI().resolve(location);
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw ApiError.INVALID_INPUT.createException("LCP download is unavailable. Download a new license from your provider.");
                    }
                    if (response.getHeaders().getContentLength() > limit) {
                        throw ApiError.INVALID_INPUT.createException("LCP publication exceeds the upload size limit.");
                    }
                    try (var stream = Files.newOutputStream(output)) {
                        byte[] buffer = new byte[16 * 1024];
                        long total = 0;
                        int count;
                        while ((count = response.getBody().read(buffer)) != -1) {
                            total += count;
                            if (total > limit) throw ApiError.INVALID_INPUT.createException("LCP publication exceeds the upload size limit.");
                            stream.write(buffer, 0, count);
                        }
                    }
                    return null;
                });
                if (next == null) return;
                uri = next;
            }
            throw ApiError.INVALID_INPUT.createException("Too many LCP download redirects.");
        } catch (APIException exception) { throw exception; }
        catch (RestClientException exception) {
            // Do not expose signed download URLs or log license credentials.
            throw ApiError.INVALID_INPUT.createException("LCP download failed. Check the license and the server's outbound network policy.");
        }
    }
}
