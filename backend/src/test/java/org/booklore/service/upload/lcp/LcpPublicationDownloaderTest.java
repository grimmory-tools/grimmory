package org.booklore.service.upload.lcp;

import org.booklore.config.AppProperties;
import org.booklore.config.RestClientConfig;
import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LcpPublicationDownloaderTest {
    @TempDir Path directory;

    @Test
    void followsHttpsRedirectAndWritesBoundedPublication() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.org/license-book"))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "/book.epub"));
        server.expect(requestTo("https://example.org/book.epub"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));
        Path output = directory.resolve("book.epub");
        new LcpPublicationDownloader(builder.build()).download(URI.create("https://example.org/license-book"), output, 16);
        assertThat(Files.readAllBytes(output)).containsExactly(1, 2, 3);
        server.verify();
    }

    @Test
    void rejectsOversizedResponsesAndUnsafeRedirects() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example.org/large"))
                .andRespond(withSuccess(new byte[17], MediaType.APPLICATION_OCTET_STREAM));
        server.expect(requestTo("https://example.org/redirect"))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "http://127.0.0.1/private"));
        var downloader = new LcpPublicationDownloader(builder.build());
        assertThatThrownBy(() -> downloader.download(URI.create("https://example.org/large"), directory.resolve("large.epub"), 16))
                .isInstanceOf(APIException.class).hasMessageContaining("size limit");
        assertThatThrownBy(() -> downloader.download(URI.create("https://example.org/redirect"), directory.resolve("redirect.epub"), 16))
                .isInstanceOf(APIException.class).hasMessageContaining("HTTPS");
        server.verify();
    }

    @Test
    void privateAddressIsBlockedAtConnectionTime() {
        AppProperties properties = new AppProperties();
        properties.setOutbound(new AppProperties.OutboundRequests());
        var client = new RestClientConfig(properties).lcpRestClient(InetAddressFilter.all());
        assertThatThrownBy(() -> client.get().uri("https://127.0.0.1:443/").retrieve().toBodilessEntity())
                .hasStackTraceContaining("FilteredHostException");
    }

    @Test
    void rejectsCredentialsHttpAndNonstandardPorts() {
        for (String uri : new String[]{"http://example.org/book", "https://user:secret@example.org/book", "https://example.org:8080/book"}) {
            assertThatThrownBy(() -> LcpPublicationDownloader.validateUri(URI.create(uri))).isInstanceOf(APIException.class);
        }
    }
}
