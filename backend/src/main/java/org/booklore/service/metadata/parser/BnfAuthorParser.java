package org.booklore.service.metadata.parser;

import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.AuthorSearchResult;
import org.booklore.model.enums.AuthorMetadataSource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BnfAuthorParser implements AuthorParser {

    private static final String SPARQL_ENDPOINT = "https://data.bnf.fr/sparql";
    private static final String FOAF_NAME = "http://xmlns.com/foaf/0.1/name";
    private static final String DCTERMS_DESCRIPTION = "http://purl.org/dc/terms/description";
    private static final String OWL_SAME_AS = "http://www.w3.org/2002/07/owl#sameAs";
    private static final Pattern AUTHOR_URI = Pattern.compile(
            "^https?://data\\.bnf\\.fr/ark:/12148/[A-Za-z0-9._-]+#about$");
    private static final Pattern ISNI = Pattern.compile("(?i)isni\\.org/isni/([0-9]{15}[0-9X])");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BnfAuthorParser(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AuthorSearchResult> searchAuthors(String name, String region) {
        if (name == null || name.isBlank()) {
            return List.of();
        }

        String literal = escapeSparqlLiteral(name.trim());
        String query = """
                PREFIX foaf: <http://xmlns.com/foaf/0.1/>
                PREFIX dcterms: <http://purl.org/dc/terms/>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT DISTINCT ?uri ?name ?description ?sameAs WHERE {
                  ?uri foaf:name ?name .
                  FILTER(CONTAINS(LCASE(STR(?name)), LCASE("%s")))
                  OPTIONAL { ?uri dcterms:description ?description }
                  OPTIONAL {
                    ?uri owl:sameAs ?sameAs .
                    FILTER(CONTAINS(LCASE(STR(?sameAs)), "isni.org/isni/"))
                  }
                }
                LIMIT 20
                """.formatted(literal);

        return execute(query);
    }

    @Override
    public AuthorSearchResult getAuthorById(String providerId, String region) {
        if (providerId == null || !AUTHOR_URI.matcher(providerId).matches()) {
            return null;
        }

        String query = """
                PREFIX foaf: <http://xmlns.com/foaf/0.1/>
                PREFIX dcterms: <http://purl.org/dc/terms/>
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                SELECT DISTINCT ?uri ?name ?description ?sameAs WHERE {
                  VALUES ?uri { <%s> }
                  ?uri foaf:name ?name .
                  OPTIONAL { ?uri dcterms:description ?description }
                  OPTIONAL {
                    ?uri owl:sameAs ?sameAs .
                    FILTER(CONTAINS(LCASE(STR(?sameAs)), "isni.org/isni/"))
                  }
                }
                LIMIT 1
                """.formatted(providerId);
        return execute(query).stream().findFirst().orElse(null);
    }

    private List<AuthorSearchResult> execute(String query) {
        try {
            URI uri = URI.create(SPARQL_ENDPOINT + "?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&format=application%2Fsparql-results%2Bjson");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Accept", "application/sparql-results+json")
                    .header("User-Agent", "Grimmory")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("BnF author query returned status {}", response.statusCode());
                return List.of();
            }

            JsonNode bindings = objectMapper.readTree(response.body()).path("results").path("bindings");
            Map<String, AuthorSearchResult> resultsById = new LinkedHashMap<>();
            for (JsonNode binding : bindings) {
                String uriValue = bindingValue(binding, "uri");
                String name = bindingValue(binding, "name");
                if (uriValue == null || name == null || name.isBlank()) {
                    continue;
                }
                String sameAs = bindingValue(binding, "sameAs");
                AuthorSearchResult result = resultsById.computeIfAbsent(uriValue, id ->
                        AuthorSearchResult.builder()
                                .source(AuthorMetadataSource.BNF)
                                .providerId(id)
                                .name(name)
                                .build());
                if (result.getDescription() == null) {
                    result.setDescription(bindingValue(binding, "description"));
                }
                if (result.getIsni() == null && sameAs != null) {
                    result.setIsni(extractIsni(sameAs));
                }
            }
            return new ArrayList<>(resultsById.values());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("BnF author query interrupted", e);
            return List.of();
        } catch (IOException | IllegalArgumentException e) {
            log.warn("BnF author query failed", e);
            return List.of();
        }
    }

    private String bindingValue(JsonNode binding, String key) {
        JsonNode value = binding.path(key).path("value");
        return value.isTextual() ? value.asText() : null;
    }

    private String extractIsni(String sameAs) {
        Matcher matcher = ISNI.matcher(sameAs);
        return matcher.find() ? matcher.group(1).toUpperCase() : null;
    }

    private String escapeSparqlLiteral(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
