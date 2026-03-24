package org.booklore.service.metadata.extractor;

import io.documentnode.epub4j.domain.Book;
import io.documentnode.epub4j.domain.MediaType;
import io.documentnode.epub4j.domain.MediaTypes;
import io.documentnode.epub4j.domain.Resource;
import io.documentnode.epub4j.epub.EpubReader;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.booklore.model.dto.BookMetadata;
import org.booklore.service.metadata.BookLoreMetadata;
import org.booklore.service.reader.EpubReaderService;
import org.booklore.util.SecureXmlUtils;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EpubMetadataExtractor implements FileMetadataExtractor {

    private static final Pattern YEAR_ONLY_PATTERN = Pattern.compile("^\\d{4}$");
    private static final String OPF_NS = "http://www.idpf.org/2007/opf";

    // List of all media types that epub4j has so we can lazy load them.
    // Note that we have to add in null to handle files without extentions like mimetype.
    private static final List<MediaType> MEDIA_TYPES = new ArrayList<>();
    private static final Pattern ISBN_SEPARATOR_PATTERN = Pattern.compile("[- ]");

    private static final Set<Integer> VALID_AGE_RATINGS = Set.of(0, 6, 10, 13, 16, 18, 21);

    static {
        MEDIA_TYPES.addAll(Arrays.asList(MediaTypes.mediaTypes));
        MEDIA_TYPES.add(null);
    }

    private static final Map<String, BiConsumer<BookMetadata.BookMetadataBuilder, String>> CALIBRE_IDENTIFIER_PREFIXES = Map.of(
            "amazon", BookMetadata.BookMetadataBuilder::asin,
            "asin", BookMetadata.BookMetadataBuilder::asin,
            "mobi-asin", BookMetadata.BookMetadataBuilder::asin,
            "goodreads", BookMetadata.BookMetadataBuilder::goodreadsId,
            "google", BookMetadata.BookMetadataBuilder::googleId,
            "hardcover", BookMetadata.BookMetadataBuilder::hardcoverId,
            "hardcover_book", BookMetadata.BookMetadataBuilder::hardcoverBookId,
            "comicvine", BookMetadata.BookMetadataBuilder::comicvineId,
            "lubimyczytac", BookMetadata.BookMetadataBuilder::lubimyczytacId,
            "ranobedb", BookMetadata.BookMetadataBuilder::ranobedbId);

    private static final Map<String, BiConsumer<BookMetadata.BookMetadataBuilder, String>> CALIBRE_FIELD_MAPPINGS = Map.ofEntries(
            Map.entry("#subtitle", BookMetadata.BookMetadataBuilder::subtitle),
            Map.entry("#pagecount", (builder, value) -> safeParseInt(value, builder::pageCount)),
            Map.entry("#series_total", (builder, value) -> safeParseInt(value, builder::seriesTotal)),
            Map.entry("#amazon_rating", (builder, value) -> safeParseDouble(value, builder::amazonRating)),
            Map.entry("#amazon_review_count", (builder, value) -> safeParseInt(value, builder::amazonReviewCount)),
            Map.entry("#goodreads_rating", (builder, value) -> safeParseDouble(value, builder::goodreadsRating)),
            Map.entry("#goodreads_review_count", (builder, value) -> safeParseInt(value, builder::goodreadsReviewCount)),
            Map.entry("#hardcover_rating", (builder, value) -> safeParseDouble(value, builder::hardcoverRating)),
            Map.entry("#hardcover_review_count", (builder, value) -> safeParseInt(value, builder::hardcoverReviewCount)),
            Map.entry("#lubimyczytac_rating", (builder, value) -> safeParseDouble(value, builder::lubimyczytacRating)),
            Map.entry("#ranobedb_rating", (builder, value) -> safeParseDouble(value, builder::ranobedbRating)),
            Map.entry("#age_rating", (builder, value) -> safeParseInt(value, v -> {
                if (VALID_AGE_RATINGS.contains(v)) builder.ageRating(v);
            })),
            Map.entry("#content_rating", (builder, value) -> {
                String normalized = value.trim().toUpperCase();
                if (Set.of("EVERYONE", "TEEN", "MATURE", "ADULT", "EXPLICIT").contains(normalized)) {
                    builder.contentRating(normalized);
                }
            }));

    @Override
    public byte[] extractCover(File epubFile) {
        Book epub = null;
        io.documentnode.epub4j.domain.Resource coverImage = null;

        try (FileInputStream fis = new FileInputStream(epubFile)) {
            try {
                epub = new EpubReader().readEpub(fis);
                coverImage = epub.getCoverImage();
            } catch (NullPointerException e) {
                log.debug("epub4j threw NullPointerException (likely malformed NCX) in {}: {}", epubFile.getName(), e.getMessage());
            } catch (Exception e) {
                log.debug("epub4j failed to parse EPUB for cover extraction (will try fallbacks) in {}: {}", epubFile.getName(), e.getMessage());
            }

            // We fall back to reading the image based on the cover-image property.
            String coverHref = findCoverImageHrefInOpf(epubFile);
            if (coverHref != null) {
                byte[] image = extractFileFromZip(epubFile, coverHref);
                if (image != null) {
                    return image;
                }
            }

            // Fallback: search manifest for anything that looks like a cover
            if (coverImage == null) {
                String href = findManifestCoverByHeuristic(epubFile);
                if (href != null) {
                    byte[] data = extractFileFromZip(epubFile, href);
                    if (data != null) return data;
                }
            }

            if (coverImage == null && epub != null) {
                for (io.documentnode.epub4j.domain.Resource res : epub.getResources().getAll()) {
                    String id = res.getId();
                    String href = res.getHref();
                    if ((id != null && id.toLowerCase().contains("cover")) ||
                            (href != null && href.toLowerCase().contains("cover"))) {
                        if (res.getMediaType() != null && res.getMediaType().getName().startsWith("image")) {
                            coverImage = res;
                            break;
                        }
                    }
                }
            }

            // Absolute last resort: Scan ZIP entries directly
            if (coverImage == null) {
                byte[] data = findCoverInZipByHeuristic(epubFile);
                if (data != null) return data;
            }

            return (coverImage != null) ? coverImage.getData() : null;
        } catch (Exception e) {
            log.warn("Failed to extract cover from EPUB: {}", epubFile.getName(), e);
        }

        return null;
    }

    private String findManifestCoverByHeuristic(File epubFile) {
        try {
            Document doc = EpubReaderService.getOPFDocument(epubFile);

            NodeList manifestItems = doc.getElementsByTagName("item");

            for (int i = 0; i < manifestItems.getLength(); i++) {
                Element item = (Element) manifestItems.item(i);
                String id = item.getAttribute("id");
                String href = item.getAttribute("href");
                String mediaType = item.getAttribute("media-type");

                if ((id != null && id.toLowerCase().contains("cover")) || (href != null && href.toLowerCase().contains("cover"))) {
                    if (mediaType != null && mediaType.startsWith("image/")) {
                        String decodedHref = URLDecoder.decode(href, StandardCharsets.UTF_8);
                        return resolvePath(EpubReaderService.getOPFPath(epubFile), decodedHref);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Heuristic manifest search failed: {}", e.getMessage());
        }
        return null;
    }

    private byte[] findCoverInZipByHeuristic(File epubFile) {
        try (ZipFile zip = new ZipFile(epubFile)) {
            List<FileHeader> fileHeaders = zip.getFileHeaders();
            for (FileHeader header : fileHeaders) {
                String name = header.getFileName().toLowerCase();
                if (name.contains("cover") && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp"))) {
                    try (InputStream is = zip.getInputStream(header)) {
                        return is.readAllBytes();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Direct ZIP scan for cover failed: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public BookMetadata extractMetadata(File epubFile) {
        try {
            Document doc = EpubReaderService.getOPFDocument(epubFile);

            Element metadata = (Element) doc.getElementsByTagNameNS("*", "metadata").item(0);
            if (metadata == null) return null;

            BookMetadata.BookMetadataBuilder builderMeta = BookMetadata.builder();
            Set<String> categories = new HashSet<>();
            Set<String> moods = new HashSet<>();
            Set<String> tags = new HashSet<>();

            boolean seriesFound = false;
            boolean seriesIndexFound = false;

            NodeList children = metadata.getChildNodes();

            Map<String, String> creatorsById = new HashMap<>();
            Map<String, String> creatorRoleById = new HashMap<>();
            Map<String, List<String>> creatorsByRole = new HashMap<>();
            creatorsByRole.put("aut", new ArrayList<>());

            Map<String, String> titlesById = new HashMap<>();
            Map<String, String> titleTypeById = new HashMap<>();

            for (int i = 0; i < children.getLength(); i++) {
                if (!(children.item(i) instanceof Element el)) continue;

                String tag = el.getLocalName();
                String text = el.getTextContent().trim();

                switch (tag) {
                    case "title" -> {
                        String id = el.getAttribute("id");
                        if (StringUtils.isNotBlank(id)) {
                            titlesById.put(id, text);
                        } else {
                            builderMeta.title(text);
                        }
                    }
                    case "meta" -> {
                        String prop = el.getAttribute("property").trim();
                        String name = el.getAttribute("name").trim();
                        String refines = el.getAttribute("refines").trim();
                        String content = el.hasAttribute("content") ? el.getAttribute("content").trim() : text;

                        if ("title-type".equals(prop) && StringUtils.isNotBlank(refines)) {
                            titleTypeById.put(refines.substring(1), content.toLowerCase());
                        }

                        if ("role".equals(prop) && StringUtils.isNotBlank(refines)) {
                            creatorRoleById.put(refines.substring(1), content.toLowerCase());
                        }

                        if ("rendition:layout".equals(prop) && "pre-paginated".equals(content)) {
                            builderMeta.isFixedLayout(true);
                        }

                        if (!seriesFound && ((BookLoreMetadata.NS_PREFIX + ":series").equals(prop) || "calibre:series".equals(name) || "belongs-to-collection".equals(prop))) {
                            builderMeta.seriesName(content);
                            seriesFound = true;
                        }
                        if (!seriesIndexFound && ((BookLoreMetadata.NS_PREFIX + ":series_index").equals(prop) || "calibre:series_index".equals(name) || "group-position".equals(prop))) {
                            try {
                                builderMeta.seriesNumber(Float.parseFloat(content));
                                seriesIndexFound = true;
                            } catch (NumberFormatException ignored) {
                            }
                        }

                        if ("calibre:pages".equals(name) || "pagecount".equals(name) || "schema:pagecount".equals(prop) || "media:pagecount".equals(prop) || (BookLoreMetadata.NS_PREFIX + ":page_count").equals(prop)) {
                            safeParseInt(content, builderMeta::pageCount);
                        } else if ("calibre:user_metadata:#pagecount".equals(name)) {
                            try {
                                JSONObject jsonroot = new JSONObject(content);
                                Object value = jsonroot.opt("#value#");
                                safeParseInt(String.valueOf(value), builderMeta::pageCount);
                            } catch (JSONException ignored) {
                            }
                        } else if ("calibre:user_metadata".equals(prop)) {
                            try {
                                extractCalibreUserMetadata(new JSONObject(content), builderMeta, moods, tags);
                            } catch (JSONException e) {
                                log.warn("Failed to parse Calibre user_metadata JSON: {}", e.getMessage());
                            }
                        }



                        String key = StringUtils.isNotBlank(prop) ? prop : name;

                        if (key.equals(BookLoreMetadata.NS_PREFIX + ":asin")) builderMeta.asin(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":goodreads_id")) builderMeta.goodreadsId(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":comicvine_id")) builderMeta.comicvineId(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":ranobedb_id")) builderMeta.ranobedbId(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":hardcover_id")) builderMeta.hardcoverId(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":google_books_id")) builderMeta.googleId(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":lubimyczytac_id")) builderMeta.lubimyczytacId(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":page_count")) safeParseInt(content, builderMeta::pageCount);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":subtitle")) builderMeta.subtitle(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":series_total")) safeParseInt(content, builderMeta::seriesTotal);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":rating")) { /* Generic rating not supported */ }
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":amazon_rating")) safeParseDouble(content, builderMeta::amazonRating);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":amazon_review_count")) safeParseInt(content, builderMeta::amazonReviewCount);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":goodreads_rating")) safeParseDouble(content, builderMeta::goodreadsRating);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":goodreads_review_count")) safeParseInt(content, builderMeta::goodreadsReviewCount);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":hardcover_rating")) safeParseDouble(content, builderMeta::hardcoverRating);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":hardcover_review_count")) safeParseInt(content, builderMeta::hardcoverReviewCount);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":lubimyczytac_rating")) safeParseDouble(content, builderMeta::lubimyczytacRating);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":ranobedb_rating")) safeParseDouble(content, builderMeta::ranobedbRating);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":age_rating")) safeParseInt(content, v -> { if (VALID_AGE_RATINGS.contains(v)) builderMeta.ageRating(v); });
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":content_rating")) builderMeta.contentRating(content);
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":moods")) {
                            if (StringUtils.isNotBlank(content)) {
                                extractSetField(content, moods);
                            }
                        }
                        else if (key.equals(BookLoreMetadata.NS_PREFIX + ":tags")) {
                            if (StringUtils.isNotBlank(content)) {
                                extractSetField(content, tags);
                            }
                        }
                    }
                    case "creator" -> {
                        String role = el.getAttributeNS(OPF_NS, "role");
                        if (StringUtils.isNotBlank(role)) {
                            creatorsByRole.computeIfAbsent(role, k -> new ArrayList<>()).add(text);
                        } else {
                            String id = el.getAttribute("id");
                            if (StringUtils.isNotBlank(id)) {
                                creatorsById.put(id, text);
                            } else {
                                creatorsByRole.get("aut").add(text);
                            }
                        }
                    }
                    case "subject" -> categories.add(text);
                    case "description" -> builderMeta.description(text);
                    case "publisher" -> builderMeta.publisher(text);
                    case "language" -> builderMeta.language(text);
                    case "identifier" -> {
                        String scheme = el.getAttributeNS(OPF_NS, "scheme").toUpperCase();
                        String textLower = text.toLowerCase();

                        // Parse URN format: urn:scheme:value
                        String value = text;
                        String urnScheme = null;
                        if (textLower.startsWith("urn:")) {
                            String[] parts = text.split(":", 3);
                            if (parts.length >= 3) {
                                urnScheme = parts[1].toUpperCase();
                                value = parts[2];
                            }
                        } else if (textLower.startsWith("isbn:")) {
                            value = text.substring(5);
                            urnScheme = "ISBN";
                        }

                        // Use URN scheme if opf:scheme is not present
                        if (scheme.isEmpty() && urnScheme != null) {
                            scheme = urnScheme;
                        }

                        if (!scheme.isEmpty()) {
                            switch (scheme) {
                                case "ISBN", "ISBN10", "ISBN13" -> {
                                    String cleanValue = ISBN_SEPARATOR_PATTERN.matcher(value).replaceAll("");
                                    if (cleanValue.length() == 13) builderMeta.isbn13(value);
                                    else if (cleanValue.length() == 10) builderMeta.isbn10(value);
                                }
                                case "GOODREADS" -> builderMeta.goodreadsId(value);
                                case "COMICVINE" -> builderMeta.comicvineId(value);
                                case "RANOBEDB" -> builderMeta.ranobedbId(value);
                                case "GOOGLE" -> builderMeta.googleId(value);
                                case "AMAZON" -> builderMeta.asin(value);
                                case "HARDCOVER" -> builderMeta.hardcoverId(value);
                                case "HARDCOVERBOOK", "HARDCOVER_BOOK_ID" -> builderMeta.hardcoverBookId(value);
                                case "LUBIMYCZYTAC" -> builderMeta.lubimyczytacId(value);
                            }
                        } else {
                            // Handle Calibre's prefix:value format (e.g., amazon:B09XXX, goodreads:123)
                            int colonIdx = text.indexOf(':');
                            if (colonIdx > 0) {
                                String prefix = text.substring(0, colonIdx).toLowerCase();
                                String val = text.substring(colonIdx + 1).trim();
                                if (!"calibre".equals(prefix) && !"uuid".equals(prefix)) {
                                    BiConsumer<BookMetadata.BookMetadataBuilder, String> setter = CALIBRE_IDENTIFIER_PREFIXES.get(prefix);
                                    if (setter != null) {
                                        setter.accept(builderMeta, val);
                                    }
                                }
                            }
                        }
                    }
                    case "date" -> {
                        LocalDate parsed = parseDate(text);
                        if (parsed != null) builderMeta.publishedDate(parsed);
                    }
                }
            }

            for (Map.Entry<String, String> entry : titlesById.entrySet()) {
                String id = entry.getKey();
                String value = entry.getValue();
                String type = titleTypeById.getOrDefault(id, "main");
                if ("main".equals(type)) builderMeta.title(value);
                else if ("subtitle".equals(type)) builderMeta.subtitle(value);
            }

            if (builderMeta.build().getPublishedDate() == null) {
                for (int i = 0; i < children.getLength(); i++) {
                    if (!(children.item(i) instanceof Element el)) continue;
                    if (!"meta".equals(el.getLocalName())) continue;
                    String prop = el.getAttribute("property").trim().toLowerCase();
                    String content = el.hasAttribute("content") ? el.getAttribute("content").trim() : el.getTextContent().trim();
                    if ("dcterms:modified".equals(prop)) {
                        LocalDate parsed = parseDate(content);
                        if (parsed != null) {
                            builderMeta.publishedDate(parsed);
                            break;
                        }
                    }
                }
            }

            for (Map.Entry<String, String> entry : creatorsById.entrySet()) {
                String id = entry.getKey();
                String value = entry.getValue();
                String role = creatorRoleById.getOrDefault(id, "aut");
                creatorsByRole.computeIfAbsent(role, k -> new ArrayList<>()).add(value);
            }

            builderMeta.authors(creatorsByRole.get("aut"));

            if (!moods.isEmpty()) builderMeta.moods(moods);
            if (!tags.isEmpty()) builderMeta.tags(tags);

            // Remove moods and tags from categories to ensure strict separation
            categories.removeAll(moods);
            categories.removeAll(tags);

            builderMeta.categories(categories);

            BookMetadata extractedMetadata = builderMeta.build();

            if (StringUtils.isBlank(extractedMetadata.getTitle())) {
                builderMeta.title(FilenameUtils.getBaseName(epubFile.getName()));
                extractedMetadata = builderMeta.build();
            }

            return extractedMetadata;
        } catch (Exception e) {
            log.error("Failed to read metadata from EPUB file {}: {}", epubFile.getName(), e.getMessage(), e);
            return null;
        }
    }

    private static void safeParseInt(String value, java.util.function.IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void safeParseDouble(String value, java.util.function.DoubleConsumer setter) {
        try {
            setter.accept(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
        }
    }
    
    private void extractSetField(String value, Set<String> targetSet) {
        if (StringUtils.isNotBlank(value)) {
            targetSet.addAll(parseJsonArrayOrCsv(value));
        }
    }

    private void extractCalibreUserMetadata(JSONObject userMetadata, BookMetadata.BookMetadataBuilder builder,
                                             Set<String> moodsSet, Set<String> tagsSet) {
        Iterator<String> keys = userMetadata.keys();
        while (keys.hasNext()) {
            String fieldName = keys.next();
            try {
                JSONObject fieldObj = userMetadata.optJSONObject(fieldName);
                if (fieldObj == null) continue;

                Object rawValue = fieldObj.opt("#value#");
                if (rawValue == null) continue;

                String value = String.valueOf(rawValue).trim();
                if (value.isEmpty() || "null".equals(value)) continue;

                if ("#moods".equals(fieldName)) {
                    extractSetField(value, moodsSet);
                } else if ("#extra_tags".equals(fieldName)) {
                    extractSetField(value, tagsSet);
                } else {
                    BiConsumer<BookMetadata.BookMetadataBuilder, String> mapper = CALIBRE_FIELD_MAPPINGS.get(fieldName);
                    if (mapper != null) {
                        mapper.accept(builder, value);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract Calibre field '{}': {}", fieldName, e.getMessage());
            }
        }
    }

    /**
     * Parses a string that may be either a JSON array (e.g., ["item1", "item2"]) or a CSV (item1, item2).
     * Returns a Set of parsed values.
     */
    private Set<String> parseJsonArrayOrCsv(String content) {
        if (StringUtils.isBlank(content)) {
            return new HashSet<>();
        }
        
        content = content.trim();
        
        // Check if it looks like a JSON array
        if (content.startsWith("[") && content.endsWith("]")) {
            // Remove brackets
            String inner = content.substring(1, content.length() - 1).trim();
            if (inner.isEmpty()) {
                return new HashSet<>();
            }

            // Split by comma and parse each quoted item
            return Arrays.stream(inner.split(","))
                    .map(String::trim)
                    .map(s -> {
                        // Remove surrounding quotes if present
                        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                            return s.substring(1, s.length() - 1);
                        }
                        return s;
                    })
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
        }

        // Fallback to CSV parsing
        return Arrays.stream(content.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
    }

    private LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) return null;

        value = value.trim();

        // Check for year-only format first (e.g., "2024") - common in EPUB metadata
        if (YEAR_ONLY_PATTERN.matcher(value).matches()) {
            int year = Integer.parseInt(value);
            if (year >= 1 && year <= 9999) {
                return LocalDate.of(year, 1, 1);
            }
        }

        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
        }

        // Try parsing first 10 characters for ISO date format with extra content
        if (value.length() >= 10) {
            try {
                return LocalDate.parse(value.substring(0, 10));
            } catch (Exception ignored) {
            }
        }

        log.warn("Failed to parse date from string: {}", value);
        return null;
    }

    private byte[] getImageFromEpubResource(Resource res) {
        if (res == null) {
            return null;
        }

        MediaType mt = res.getMediaType();
        if (mt == null || mt.getName() == null || !mt.getName().startsWith("image")) {
            return null;
        }

        try {
            return res.getData();
        } catch (IOException e) {
            log.warn("Failed to read data for resource", e);
            return null;
        }
    }

    private String findCoverImageHrefInOpf(File epubFile) {
        try {
            Document doc = EpubReaderService.getOPFDocument(epubFile);
            NodeList manifestItems = doc.getElementsByTagName("item");

            for (int i = 0; i < manifestItems.getLength(); i++) {
                Element item = (Element) manifestItems.item(i);
                String properties = item.getAttribute("properties");
                if (properties != null && properties.contains("cover-image")) {
                    String href = item.getAttribute("href");
                    String decodedHref = URLDecoder.decode(href, StandardCharsets.UTF_8);
                    return resolvePath(EpubReaderService.getOPFPath(epubFile), decodedHref);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to find cover image in OPF: {}", e.getMessage());
        }
        return null;
    }

    private String resolvePath(String opfPath, String href) {
        if (href == null || href.isEmpty()) return null;

        // If href is absolute within the zip (starts with /), return it without leading /
        if (href.startsWith("/")) return href.substring(1);

        int lastSlash = opfPath.lastIndexOf('/');
        String basePath = (lastSlash == -1) ? "" : opfPath.substring(0, lastSlash + 1);

        String combined = basePath + href;

        // Normalize path components to handle ".." and "."
        java.util.LinkedList<String> parts = new java.util.LinkedList<>();
        for (String part : combined.split("/")) {
            if ("..".equals(part)) {
                if (!parts.isEmpty()) parts.removeLast();
            } else if (!".".equals(part) && !part.isEmpty()) {
                parts.add(part);
            }
        }

        return String.join("/", parts);
    }

    private byte[] extractFileFromZip(File epubFile, String path) {
        try (ZipFile zip = new ZipFile(epubFile)) {
            FileHeader header = zip.getFileHeader(path);
            if (header == null) return null;
            try (InputStream is = zip.getInputStream(header)) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("Failed to extract file {} from zip", path);
            return null;
        }
    }
}
