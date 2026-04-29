package org.booklore.service.acquisition;

import org.booklore.model.dto.acquisition.NzbResult;
import org.booklore.model.entity.AcquisitionIndexerEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class NewznabClient {

    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<NzbResult> searchBooks(AcquisitionIndexerEntity indexer, String query) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(indexer.getUrl().replaceAll("/+$", "") + "/api")
                    .queryParam("t", "search")
                    .queryParam("apikey", indexer.getApiKey())
                    .queryParam("cat", "7020,7000")
                    .queryParam("q", query)
                    .build()
                    .toUri();

            log.info("Querying indexer '{}': {}", indexer.getName(),
                    uri.toString().replaceAll("apikey=[^&]+", "apikey=***"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/xml, text/xml, */*")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Indexer '{}' returned HTTP {}", indexer.getName(), response.statusCode());
                return List.of();
            }

            List<NzbResult> results = parseXmlResponse(response.body(), indexer.getName());
            log.info("Indexer '{}' returned {} results for query '{}'", indexer.getName(), results.size(), query);
            return results;

        } catch (Exception e) {
            log.warn("Indexer '{}' search failed for query '{}': {}", indexer.getName(), query, e.getMessage());
            return List.of();
        }
    }

    private List<NzbResult> parseXmlResponse(String xml, String indexerName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList items = doc.getElementsByTagName("item");
            if (items.getLength() == 0) {
                log.debug("Indexer '{}': no <item> elements in response", indexerName);
                return List.of();
            }

            List<NzbResult> results = new ArrayList<>();
            for (int i = 0; i < items.getLength(); i++) {
                if (!(items.item(i) instanceof Element item)) continue;
                NzbResult result = parseItem(item, indexerName);
                if (result != null) results.add(result);
            }
            return results;

        } catch (Exception e) {
            log.warn("Failed to parse XML from '{}': {}", indexerName, e.getMessage());
            return List.of();
        }
    }

    private NzbResult parseItem(Element item, String indexerName) {
        try {
            String title = getText(item, "title");
            String downloadUrl = getEnclosureUrl(item);
            if (downloadUrl == null || downloadUrl.isBlank()) {
                downloadUrl = getText(item, "link");
            }
            if (title.isBlank() || downloadUrl.isBlank()) return null;

            long sizeBytes = getEnclosureLength(item);
            Instant publishedAt = parseDate(getText(item, "pubDate"));
            int grabs = getNewznabAttr(item, "grabs");

            return new NzbResult(title, downloadUrl, sizeBytes, publishedAt, grabs, indexerName);

        } catch (Exception e) {
            log.debug("Skipping unparseable item: {}", e.getMessage());
            return null;
        }
    }

    private String getText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent().trim();
    }

    private String getEnclosureUrl(Element item) {
        NodeList enclosures = item.getElementsByTagName("enclosure");
        if (enclosures.getLength() == 0) return null;
        Element enc = (Element) enclosures.item(0);
        String url = enc.getAttribute("url");
        return url.isBlank() ? null : url;
    }

    private long getEnclosureLength(Element item) {
        NodeList enclosures = item.getElementsByTagName("enclosure");
        if (enclosures.getLength() == 0) return 0L;
        Element enc = (Element) enclosures.item(0);
        String len = enc.getAttribute("length");
        try { return Long.parseLong(len); } catch (Exception e) { return 0L; }
    }

    private int getNewznabAttr(Element item, String attrName) {
        for (String tag : new String[]{"newznab:attr", "attr"}) {
            NodeList attrs = item.getElementsByTagName(tag);
            for (int i = 0; i < attrs.getLength(); i++) {
                Element a = (Element) attrs.item(i);
                if (attrName.equals(a.getAttribute("name"))) {
                    try { return Integer.parseInt(a.getAttribute("value")); } catch (Exception e) { return 0; }
                }
            }
        }
        return 0;
    }

    private Instant parseDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return Instant.now();
        try {
            return ZonedDateTime.parse(pubDate, RFC_822).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
