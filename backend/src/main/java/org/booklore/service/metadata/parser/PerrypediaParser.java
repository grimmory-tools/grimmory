package org.booklore.service.metadata.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.BookMetadata;
import org.booklore.model.dto.request.FetchMetadataRequest;
import org.booklore.model.dto.response.perrypediaapi.PerrypediaParseResponse;
import org.booklore.model.dto.response.perrypediaapi.PerrypediaSearchResponse;
import org.booklore.model.enums.MetadataProvider;
import org.booklore.service.metadata.parser.perrypedia.InfoboxWikitextParser;
import org.booklore.util.BookUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches metadata for Perry Rhodan / Perry Rhodan Neo / Atlan Heftromane from
 * Perrypedia (https://www.perrypedia.de), a MediaWiki-based fan encyclopedia.
 * <p>
 * There is no structured query API (no Semantic MediaWiki), so this uses
 * {@code action=parse} to get both the raw wikitext (for fields the infobox
 * template exposes as literal parameters — title, subtitle, author, issue
 * number, publication date) and the rendered infobox HTML in a single
 * request. The story cycle ("Zyklus") and the cover thumbnail are only
 * available as expanded template output, not literal parameters, so both
 * are read from the rendered HTML infobox instead of the wikitext.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerrypediaParser implements BookParser, DetailedMetadataProvider {

    private static final String PERRYPEDIA_BASE_URL = "https://www.perrypedia.de";
    private static final String PERRYPEDIA_API_URL = PERRYPEDIA_BASE_URL + "/api.php";
    private static final String USER_AGENT = "Grimmory/1.0 (Book and Comic Metadata Fetcher; +https://github.com/grimmory-tools/grimmory)";

    // Icons that can appear as <img> tags in the same infobox/section-0 HTML as the cover:
    // the disambiguation-page marker, and the reading-/audio-sample icons. Matched against
    // the <img> "alt" attribute, which mirrors the wiki file name for all of these.
    private static final Set<String> NON_COVER_IMAGE_FILENAMES = Set.of(
            "Logo_Begriffsklärung.png", "Leseprobe.png", "Hörprobe.png"
    );

    // PRN must be tried before PR so "PRN389" isn't mistakenly matched as prefix "PR", number "N389".
    private static final Pattern SOURCE_ID_PATTERN = Pattern.compile("(?i)\\b(PRN|PR|A)\\s*-?\\s*(\\d{1,4})\\b");

    private static final Map<String, Integer> GERMAN_MONTHS = Map.ofEntries(
            Map.entry("januar", 1), Map.entry("februar", 2), Map.entry("märz", 3), Map.entry("april", 4),
            Map.entry("mai", 5), Map.entry("juni", 6), Map.entry("juli", 7), Map.entry("august", 8),
            Map.entry("september", 9), Map.entry("oktober", 10), Map.entry("november", 11), Map.entry("dezember", 12)
    );
    private static final Pattern GERMAN_DATE_PATTERN = Pattern.compile("(\\d{1,2})\\.\\s*(\\p{L}+)\\s*(\\d{4})");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Conservative self-imposed rate limit — no documented Perrypedia API limit was found.
    private static final int MAX_REQUESTS_PER_WINDOW = 60;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000;
    private static final long TOKEN_GENERATION_INTERVAL = RATE_LIMIT_WINDOW_MS / MAX_REQUESTS_PER_WINDOW;
    private final Object waitLock = new Object();
    private long lastTokenGenerationTime = 0;
    private long tokenCount = MAX_REQUESTS_PER_WINDOW;

    private record SourceId(String prefix, String number) {
        String key() {
            return prefix + number;
        }
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        BookMetadata metadata = fetchTopMetadata(book, fetchMetadataRequest);
        return metadata != null ? List.of(metadata) : Collections.emptyList();
    }

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        String searchText = resolveSearchText(book, fetchMetadataRequest);
        if (searchText == null) {
            log.warn("Perrypedia: no valid search term provided for metadata fetch.");
            return null;
        }

        SourceId sourceId = extractSourceId(searchText);
        if (sourceId != null) {
            BookMetadata metadata = fetchBySourceId(sourceId);
            if (metadata != null) {
                return metadata;
            }
            log.info("Perrypedia: no article found for source id '{}', falling back to title search.", sourceId.key());
        }

        return fetchBySearch(searchText);
    }

    @Override
    public BookMetadata fetchDetailedMetadata(String providerItemId) {
        if (providerItemId == null || providerItemId.isBlank()) {
            return null;
        }
        SourceId sourceId = extractSourceId(providerItemId);
        if (sourceId == null) {
            log.warn("Perrypedia: '{}' is not a recognized source id.", providerItemId);
            return null;
        }
        return fetchBySourceId(sourceId);
    }

    private String resolveSearchText(Book book, FetchMetadataRequest request) {
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle();
        }
        if (book.getPrimaryFile() != null && book.getPrimaryFile().getFileName() != null && !book.getPrimaryFile().getFileName().isBlank()) {
            return BookUtils.cleanFileName(book.getPrimaryFile().getFileName());
        }
        return null;
    }

    private SourceId extractSourceId(String text) {
        Matcher matcher = SOURCE_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String prefix = matcher.group(1).toUpperCase(Locale.ROOT);
        String number = String.valueOf(Integer.parseInt(matcher.group(2)));
        return new SourceId(prefix, number);
    }

    /** Resolves Quelle:&lt;prefix&gt;&lt;number&gt; through its redirect to the article, in one request. */
    private BookMetadata fetchBySourceId(SourceId sourceId) {
        try {
            waitForRateLimit();
            PerrypediaParseResponse.Parse parse = fetchParse(buildParseUri("Quelle:" + sourceId.key()));
            if (parse == null) {
                return null;
            }
            return toMetadata(parse.getWikitext(), parse.getText(), sourceId, parse.getTitle());
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching metadata from Perrypedia API for source id '{}'", sourceId.key(), e);
            return null;
        }
    }

    private BookMetadata fetchBySearch(String title) {
        try {
            waitForRateLimit();

            URI searchUri = UriComponentsBuilder.fromUriString(PERRYPEDIA_API_URL)
                    .queryParam("action", "query")
                    .queryParam("list", "search")
                    .queryParam("srnamespace", "0")
                    .queryParam("srlimit", "1")
                    .queryParam("format", "json")
                    .queryParam("formatversion", "2")
                    .queryParam("srsearch", title)
                    .encode()
                    .build()
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(searchUri)
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Perrypedia search API returned status code {}", response.statusCode());
                return null;
            }

            PerrypediaSearchResponse searchResponse = objectMapper.readValue(response.body(), PerrypediaSearchResponse.class);
            if (searchResponse.getQuery() == null || searchResponse.getQuery().getSearch() == null || searchResponse.getQuery().getSearch().isEmpty()) {
                return null;
            }
            String matchedTitle = searchResponse.getQuery().getSearch().getFirst().getTitle();

            waitForRateLimit();
            PerrypediaParseResponse.Parse parse = fetchParse(buildParseUri(matchedTitle));
            if (parse == null) {
                return null;
            }
            // Best-effort: a search-matched title rarely embeds the source id, so this is
            // usually null and toMetadata falls back to series-prefix + Nummer.
            SourceId sourceId = extractSourceId(parse.getTitle());
            return toMetadata(parse.getWikitext(), parse.getText(), sourceId, parse.getTitle());
        } catch (IOException | InterruptedException e) {
            log.error("Error searching Perrypedia for '{}'", title, e);
            return null;
        }
    }

    private URI buildParseUri(String pageTitle) {
        return UriComponentsBuilder.fromUriString(PERRYPEDIA_API_URL)
                .queryParam("action", "parse")
                .queryParam("page", pageTitle)
                .queryParam("redirects", "1")
                .queryParam("prop", "wikitext|text")
                .queryParam("section", "0")
                .queryParam("format", "json")
                .queryParam("formatversion", "2")
                .encode()
                .build()
                .toUri();
    }

    private PerrypediaParseResponse.Parse fetchParse(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        // TEMPORARY: dump the raw response for debugging against the live API; remove once the
        // Zyklus/action=parse switch is confirmed working end-to-end.
        log.info("Perrypedia raw response for {}: {}", uri, response.body());
        if (response.statusCode() != 200) {
            log.error("Perrypedia API returned status code {}", response.statusCode());
            return null;
        }

        PerrypediaParseResponse parsed = objectMapper.readValue(response.body(), PerrypediaParseResponse.class);
        if (parsed.getParse() == null || parsed.getParse().getWikitext() == null) {
            return null;
        }
        return parsed.getParse();
    }

    private BookMetadata toMetadata(String wikitext, String html, SourceId fallbackSourceId, String resolvedTitle) {
        Optional<InfoboxWikitextParser.InfoboxBlock> blockOpt = InfoboxWikitextParser.parse(wikitext);
        if (blockOpt.isEmpty()) {
            return null;
        }
        InfoboxWikitextParser.InfoboxBlock block = blockOpt.get();
        Map<String, String> fields = block.fields();

        String seriesPrefix = detectSeriesPrefix(block.templateName());
        String number = fields.get("Nummer");
        String perrypediaId = (seriesPrefix != null && number != null)
                ? seriesPrefix + number
                : (fallbackSourceId != null ? fallbackSourceId.key() : null);

        String title = fields.get("Titel");
        if (title == null || title.isBlank()) {
            title = resolvedTitle;
        }

        Document doc = (html == null || html.isBlank()) ? null : Jsoup.parse(html);

        return BookMetadata.builder()
                .provider(MetadataProvider.Perrypedia)
                .perrypediaId(perrypediaId)
                .title(title)
                .subtitle(fields.get("Untertitel"))
                .authors(parseAuthors(fields.get("Autor")))
                .seriesName(extractZyklus(doc))
                .seriesNumber(parseFloat(number))
                .publishedDate(parseGermanDate(fields.get("Erscheinungsdatum")))
                .language("de")
                .thumbnailUrl(extractCoverUrl(doc))
                .build();
    }

    /**
     * The infobox template name encodes which series an article belongs to:
     * "Roman Zyklus N" (classic), "Handlungszusammenfassung Neo Staffel N"
     * (Neo), or "Handlungszusammenfassung Atlan &lt;cycle name&gt;" (Atlan).
     * Used only to derive the {@code perrypediaId} prefix — the story cycle
     * itself ("Zyklus") comes from {@link #extractZyklus}, since the cycle
     * name isn't a literal template parameter for the classic/Neo series
     * (only Atlan happens to embed it directly in the template name).
     */
    private String detectSeriesPrefix(String templateName) {
        if (templateName == null) {
            return null;
        }
        String lower = templateName.toLowerCase(Locale.ROOT);
        if (lower.contains("neo")) {
            return "PRN";
        }
        if (lower.contains("atlan")) {
            return "A";
        }
        if (lower.startsWith("roman zyklus") || lower.startsWith("handlungszusammenfassung")) {
            return "PR";
        }
        return null;
    }

    /**
     * Reads the "Zyklus" (story cycle) row out of the rendered infobox table.
     * This can't come from the wikitext parameters — the cycle name is
     * template-expanded from the cycle number ("Roman Zyklus 42" -&gt;
     * "Mythos"), not passed as a literal argument — so this needs the
     * rendered HTML rather than {@link InfoboxWikitextParser}.
     */
    private String extractZyklus(Document doc) {
        if (doc == null) {
            return null;
        }
        for (Element row : doc.select("tr")) {
            Elements cells = row.select("td, th");
            if (cells.size() < 2) {
                continue;
            }
            String label = cells.getFirst().text().trim();
            if (label.equalsIgnoreCase("Zyklus:") || label.equalsIgnoreCase("Zyklus")) {
                String value = cells.get(1).text().trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /**
     * Reads the cover thumbnail out of the rendered infobox HTML — the same document already
     * parsed for {@link #extractZyklus}, so this needs no extra request. Filenames aren't a
     * predictable {@code <prefix><nnnn>.jpg} pattern across series (Atlan's is e.g.
     * {@code A800_1.JPG}), so this reads the actual {@code <img>} tag rather than constructing
     * one, skipping the handful of non-cover icons ({@link #NON_COVER_IMAGE_FILENAMES}) that can
     * appear in the same block. Prefers the widest {@code srcset} candidate over the bare
     * {@code src} thumbnail.
     */
    private String extractCoverUrl(Document doc) {
        if (doc == null) {
            return null;
        }
        for (Element img : doc.select("img")) {
            String filename = img.attr("alt");
            if (NON_COVER_IMAGE_FILENAMES.stream().anyMatch(filename::equalsIgnoreCase)) {
                continue;
            }
            String url = widestImageUrl(img);
            if (url != null && !url.isBlank()) {
                return resolveImageUrl(url);
            }
        }
        return null;
    }

    /** Picks the highest-resolution URL between an {@code <img>}'s {@code src} and {@code srcset} candidates. */
    private String widestImageUrl(Element img) {
        String best = img.attr("src");
        float bestDensity = 1.0f;
        String srcset = img.attr("srcset");
        if (srcset.isBlank()) {
            return best;
        }
        for (String candidate : srcset.split(",")) {
            String[] parts = candidate.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            float density = 1.0f;
            if (parts.length > 1 && parts[1].endsWith("x")) {
                try {
                    density = Float.parseFloat(parts[1].substring(0, parts[1].length() - 1));
                } catch (NumberFormatException ignored) {
                    // keep default density for a malformed descriptor
                }
            }
            if (density > bestDensity) {
                bestDensity = density;
                best = parts[0];
            }
        }
        return best;
    }

    private String resolveImageUrl(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return PERRYPEDIA_BASE_URL + url;
    }

    private List<String> parseAuthors(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Float parseFloat(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseGermanDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw;
        int comma = s.indexOf(',');
        if (comma >= 0) {
            s = s.substring(comma + 1).trim();
        }
        Matcher matcher = GERMAN_DATE_PATTERN.matcher(s);
        if (!matcher.find()) {
            return null;
        }
        int day = Integer.parseInt(matcher.group(1));
        Integer month = GERMAN_MONTHS.get(matcher.group(2).toLowerCase(Locale.GERMAN));
        if (month == null) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(3));
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private void waitForRateLimit() throws InterruptedException {
        while (true) {
            synchronized (waitLock) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastRequest = currentTime - lastTokenGenerationTime;

                if (timeSinceLastRequest > TOKEN_GENERATION_INTERVAL) {
                    long tokensToAdd = timeSinceLastRequest / TOKEN_GENERATION_INTERVAL;
                    tokenCount = Math.min(tokenCount + tokensToAdd, MAX_REQUESTS_PER_WINDOW);
                    lastTokenGenerationTime += tokensToAdd * TOKEN_GENERATION_INTERVAL;
                }

                if (tokenCount > 0) {
                    tokenCount--;
                    return;
                }
            }
            try {
                Thread.sleep(TOKEN_GENERATION_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limiter interrupted", e);
                throw e;
            }
        }
    }
}
