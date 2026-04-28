package org.booklore.acquisition;

import org.booklore.model.dto.acquisition.NzbResult;
import org.booklore.model.entity.AcquisitionIndexerEntity;
import org.booklore.service.acquisition.NewznabClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewznabClientTest {

    private MockWebServer server;
    private NewznabClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new NewznabClient();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private AcquisitionIndexerEntity indexer() {
        return AcquisitionIndexerEntity.builder()
                .id(1L)
                .name("TestIndexer")
                .url("http://localhost:" + server.getPort())
                .apiKey("testkey")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .build();
    }

    private static final String TWO_ITEM_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:newznab="http://www.newznab.com/DTD/2010/feeds/attributes/">
              <channel>
                <title>TestIndexer</title>
                <newznab:response offset="0" total="2"/>
                <item>
                  <title>Dune Frank Herbert EPUB MOBI</title>
                  <link>https://indexer.com/getnzb/abc123.nzb</link>
                  <enclosure url="https://indexer.com/getnzb/abc123.nzb" length="5242880" type="application/x-nzb"/>
                  <pubDate>Thu, 10 Apr 2025 12:00:00 +0000</pubDate>
                  <newznab:attr name="grabs" value="42"/>
                </item>
                <item>
                  <title>Dune Frank Herbert PDF</title>
                  <link>https://indexer.com/getnzb/def456.nzb</link>
                  <enclosure url="https://indexer.com/getnzb/def456.nzb" length="3145728" type="application/x-nzb"/>
                  <pubDate>Wed, 09 Apr 2025 10:00:00 +0000</pubDate>
                  <newznab:attr name="grabs" value="10"/>
                </item>
              </channel>
            </rss>
            """;

    @Test
    void validXmlResponse_returnsNzbResultList() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/xml")
                .setBody(TWO_ITEM_XML));

        List<NzbResult> results = client.searchBooks(indexer(), "Dune Herbert");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).title()).isEqualTo("Dune Frank Herbert EPUB MOBI");
        assertThat(results.get(0).downloadUrl()).isEqualTo("https://indexer.com/getnzb/abc123.nzb");
        assertThat(results.get(0).sizeBytes()).isEqualTo(5_242_880L);
        assertThat(results.get(0).grabs()).isEqualTo(42);
        assertThat(results.get(0).indexerName()).isEqualTo("TestIndexer");
        assertThat(results.get(1).title()).isEqualTo("Dune Frank Herbert PDF");
    }

    @Test
    void singleItemXml_returnsSingleResult() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:newznab="http://www.newznab.com/DTD/2010/feeds/attributes/">
                  <channel>
                    <item>
                      <title>Dune Frank Herbert EPUB</title>
                      <link>https://indexer.com/getnzb/single.nzb</link>
                      <enclosure url="https://indexer.com/getnzb/single.nzb" length="5242880" type="application/x-nzb"/>
                      <pubDate>Thu, 10 Apr 2025 12:00:00 +0000</pubDate>
                      <newznab:attr name="grabs" value="5"/>
                    </item>
                  </channel>
                </rss>
                """;

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/xml")
                .setBody(xml));

        List<NzbResult> results = client.searchBooks(indexer(), "Dune");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Dune Frank Herbert EPUB");
    }

    @Test
    void emptyChannel_returnsEmptyList() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>TestIndexer</title>
                    <newznab:response offset="0" total="0" xmlns:newznab="http://www.newznab.com/DTD/2010/feeds/attributes/"/>
                  </channel>
                </rss>
                """;

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/xml")
                .setBody(xml));

        List<NzbResult> results = client.searchBooks(indexer(), "NoResults");

        assertThat(results).isEmpty();
    }

    @Test
    void http500_returnsEmptyList_doesNotThrow() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        List<NzbResult> results = client.searchBooks(indexer(), "Dune");

        assertThat(results).isEmpty();
    }

    @Test
    void malformedXml_returnsEmptyList_doesNotThrow() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/xml")
                .setBody("this is not valid xml at all <<<"));

        List<NzbResult> results = client.searchBooks(indexer(), "Dune");

        assertThat(results).isEmpty();
    }

    @Test
    void indexerDown_connectionRefused_returnsEmptyList() {
        AcquisitionIndexerEntity deadIndexer = AcquisitionIndexerEntity.builder()
                .id(99L)
                .name("DeadIndexer")
                .url("http://localhost:1")
                .apiKey("key")
                .enabled(true)
                .priority(0)
                .createdAt(Instant.now())
                .build();

        List<NzbResult> results = client.searchBooks(deadIndexer, "Dune");

        assertThat(results).isEmpty();
    }

    @Test
    void trailingSlashInUrl_handledCorrectly() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/xml")
                .setBody(TWO_ITEM_XML));

        AcquisitionIndexerEntity indexerWithSlash = AcquisitionIndexerEntity.builder()
                .id(2L).name("SlashIndexer")
                .url("http://localhost:" + server.getPort() + "/")
                .apiKey("testkey").enabled(true).priority(0).createdAt(Instant.now())
                .build();

        List<NzbResult> results = client.searchBooks(indexerWithSlash, "Dune");
        assertThat(results).hasSize(2);
    }
}
