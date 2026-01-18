package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.BookReview;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.BookUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class AmazonBookParser implements BookParser {

    private static final int COUNT_DETAILED_METADATA_TO_GET = 3;
    private static final String BASE_BOOK_URL_SUFFIX = "/dp/";
    private static final Pattern NON_DIGIT_PATTERN = Pattern.compile("[^\\d]");
    private static final Pattern SERIES_FORMAT_PATTERN = Pattern.compile("Book (\\d+(?:\\.\\d+)?) of (\\d+)");
    private static final Pattern PARENTHESES_WITH_WHITESPACE_PATTERN = Pattern.compile("\\s*\\(.*?\\)");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^\\p{L}\\p{M}0-9]");
    private static final Pattern DP_SEPARATOR_PATTERN = Pattern.compile("/dp/");
    private static final Pattern REVIEWED_IN_ON_PATTERN = Pattern.compile("(?i)(?:Reviewed in|Rezension aus|Beoordeeld in|Recensie uit|Commenté en|Recensito in|Revisado en)\\s+(.+?)\\s+(?:on|vom|op|le|il|el)\\s+(.+)");
    private static final Pattern JAPANESE_REVIEW_DATE_PATTERN = Pattern.compile("(\\d{4}年\\d{1,2}月\\d{1,2}日).+");
    private static final String[] TITLE_SELECTORS = {"#productTitle", "#ebooksProductTitle", "h1#title", "span#productTitle"};
    private static final String[] DATE_PATTERNS = {
            "MMMM d, yyyy", "d MMMM yyyy", "d. MMMM yyyy", "MMM d, yyyy",
            "MMM. d, yyyy", "d MMM yyyy", "d MMM. yyyy", "d. MMM yyyy",
            "yyyy/M/d", "yyyy/MM/dd", "yyyy年M月d日"
    };

    private static final Map<String, LocaleInfo> DOMAIN_LOCALE_MAP = Map.ofEntries(
            Map.entry("com", new LocaleInfo("en-US,en;q=0.9", Locale.US)),
            Map.entry("co.uk", new LocaleInfo("en-GB,en;q=0.9", Locale.UK)),
            Map.entry("de", new LocaleInfo("en-GB,en;q=0.9,de;q=0.8", Locale.GERMANY)),
            Map.entry("fr", new LocaleInfo("en-GB,en;q=0.9,fr;q=0.8", Locale.FRANCE)),
            Map.entry("it", new LocaleInfo("en-GB,en;q=0.9,it;q=0.8", Locale.ITALY)),
            Map.entry("es", new LocaleInfo("en-GB,en;q=0.9,es;q=0.8", new Locale.Builder().setLanguage("es").setRegion("ES").build())),
            Map.entry("ca", new LocaleInfo("en-US,en;q=0.9", Locale.CANADA)),
            Map.entry("com.au", new LocaleInfo("en-GB,en;q=0.9", new Locale.Builder().setLanguage("en").setRegion("AU").build())),
            Map.entry("co.jp", new LocaleInfo("en-GB,en;q=0.9,ja;q=0.8", Locale.JAPAN)),
            Map.entry("in", new LocaleInfo("en-GB,en;q=0.9", new Locale.Builder().setLanguage("en").setRegion("IN").build())),
            Map.entry("com.br", new LocaleInfo("en-GB,en;q=0.9,pt;q=0.8", new Locale.Builder().setLanguage("pt").setRegion("BR").build())),
            Map.entry("com.mx", new LocaleInfo("en-US,en;q=0.9,es;q=0.8", new Locale.Builder().setLanguage("es").setRegion("MX").build())),
            Map.entry("nl", new LocaleInfo("en-GB,en;q=0.9,nl;q=0.8", new Locale.Builder().setLanguage("nl").setRegion("NL").build())),
            Map.entry("se", new LocaleInfo("en-GB,en;q=0.9,sv;q=0.8", new Locale.Builder().setLanguage("sv").setRegion("SE").build())),
            Map.entry("pl", new LocaleInfo("en-GB,en;q=0.9,pl;q=0.8", new Locale.Builder().setLanguage("pl").setRegion("PL").build())),
            Map.entry("ae", new LocaleInfo("en-US,en;q=0.9,ar;q=0.8", new Locale.Builder().setLanguage("en").setRegion("AE").build())),
            Map.entry("sa", new LocaleInfo("en-US,en;q=0.9,ar;q=0.8", new Locale.Builder().setLanguage("en").setRegion("SA").build())),
            Map.entry("cn", new LocaleInfo("zh-CN,zh;q=0.9", Locale.CHINA)),
            Map.entry("sg", new LocaleInfo("en-GB,en;q=0.9", new Locale.Builder().setLanguage("en").setRegion("SG").build())),
            Map.entry("tr", new LocaleInfo("en-GB,en;q=0.9,tr;q=0.8", new Locale.Builder().setLanguage("tr").setRegion("TR").build())),
            Map.entry("eg", new LocaleInfo("en-US,en;q=0.9,ar;q=0.8", new Locale.Builder().setLanguage("en").setRegion("EG").build())),
            Map.entry("com.be", new LocaleInfo("en-GB,en;q=0.9,fr;q=0.8,nl;q=0.8", new Locale.Builder().setLanguage("fr").setRegion("BE").build()))
    );

    private static final LocaleInfo DEFAULT_LOCALE_INFO = new LocaleInfo("en-US,en;q=0.9", Locale.US);

    private final AppSettingService appSettingService;

    private record LocaleInfo(String acceptLanguage, Locale locale) {}
    private record TitleInfo(String title, String subtitle) {}
    private record SeriesInfo(String name, Float number, Integer total) {}

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        LinkedList<String> amazonBookIds = getAmazonBookIds(book, fetchMetadataRequest);
        if (amazonBookIds == null || amazonBookIds.isEmpty()) {
            return null;
        }
        return getBookMetadata(amazonBookIds.getFirst());
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        LinkedList<String> amazonBookIds = Optional.ofNullable(getAmazonBookIds(book, fetchMetadataRequest))
                .map(list -> list.stream()
                        .limit(COUNT_DETAILED_METADATA_TO_GET)
                        .collect(Collectors.toCollection(LinkedList::new)))
                .orElse(new LinkedList<>());
        if (amazonBookIds.isEmpty()) {
            return null;
        }
        List<BookMetadata> fetchedBookMetadata = new ArrayList<>();
        for (String amazonBookId : amazonBookIds) {
            if (amazonBookId == null || amazonBookId.isBlank()) {
                log.debug("Skipping null or blank Amazon book ID.");
                continue;
            }
            BookMetadata metadata = getBookMetadata(amazonBookId);
            if (metadata == null) {
                log.debug("Skipping null metadata for ID: {}", amazonBookId);
                continue;
            }
            if (metadata.getTitle() == null || metadata.getTitle().isBlank() || metadata.getAuthors() == null || metadata.getAuthors().isEmpty()) {
                log.debug("Skipping metadata with missing title or author for ID: {}", amazonBookId);
                continue;
            }
            fetchedBookMetadata.add(metadata);
        }
        return fetchedBookMetadata;
    }

    private LinkedList<String> getAmazonBookIds(Book book, FetchMetadataRequest request) {
        String queryUrl = buildQueryUrl(request, book);
        if (queryUrl == null) {
            log.error("Query URL is null, cannot proceed.");
            return null;
        }
        LinkedList<String> bookIds = new LinkedList<>();
        try {
            Document doc = fetchDocument(queryUrl);
            Element searchResults = doc.select("span[data-component-type=s-search-results]").first();
            if (searchResults == null) {
                log.error("No search results found for query: {}", queryUrl);
                return null;
            }
            Elements items = searchResults.select("div[role=listitem][data-index]");
            if (items.isEmpty()) {
                log.error("No items found in the search results.");
            } else {
                for (Element item : items) {
                    if (item.text().contains("Collects books from")) {
                        log.debug("Skipping box set item (collects books): {}", extractAmazonBookId(item));
                        continue;
                    }
                    Element titleDiv = item.selectFirst("div[data-cy=title-recipe]");
                    if (titleDiv == null) {
                        log.debug("Skipping item with missing title div: {}", extractAmazonBookId(item));
                        continue;
                    }

                    String titleText = titleDiv.text().trim();
                    if (titleText.isEmpty()) {
                        log.debug("Skipping item with empty title: {}", extractAmazonBookId(item));
                        continue;
                    }

                    String lowerTitle = titleText.toLowerCase();
                    if (lowerTitle.contains("books set") || lowerTitle.contains("box set") || lowerTitle.contains("collection set") || lowerTitle.contains("summary & study guide")) {
                        log.debug("Skipping box set item (matched filtered phrase) in title: {}", extractAmazonBookId(item));
                        continue;
                    }
                    bookIds.add(extractAmazonBookId(item));
                }
            }
        } catch (Exception e) {
            log.error("Failed to get asin: {}", e.getMessage(), e);
        }
        log.info("Amazon: Found {} book ids", bookIds.size());
        return bookIds;
    }

    private String extractAmazonBookId(Element item) {
        String bookLink = null;
        for (String type : new String[]{"Paperback", "Hardcover"}) {
            Element link = item.select("a:containsOwn(" + type + ")").first();
            if (link != null) {
                bookLink = link.attr("href");
                break;
            }
        }
        if (bookLink != null) {
            return extractAsinFromUrl(bookLink);
        } else {
            return item.attr("data-asin");
        }
    }

    private String extractAsinFromUrl(String url) {
        String[] parts = DP_SEPARATOR_PATTERN.split(url);
        if (parts.length > 1) {
            String[] asinParts = parts[1].split("/");
            return asinParts[0];
        }
        return null;
    }

    private BookMetadata getBookMetadata(String amazonBookId) {
        log.info("Amazon: Fetching metadata for: {}", amazonBookId);

        String domain = appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getDomain();
        Document doc = fetchDocument("https://www.amazon." + domain + BASE_BOOK_URL_SUFFIX + amazonBookId);

        List<BookReview> reviews = appSettingService.getAppSettings()
                .getMetadataPublicReviewsSettings()
                .getProviders()
                .stream()
                .filter(cfg -> cfg.getProvider() == MetadataProvider.Amazon && cfg.isEnabled())
                .findFirst()
                .map(cfg -> getReviews(doc, cfg.getMaxReviews()))
                .orElse(Collections.emptyList());

        return buildBookMetadata(doc, amazonBookId, reviews);
    }

    private BookMetadata buildBookMetadata(Document doc, String amazonBookId, List<BookReview> reviews) {
        TitleInfo titleInfo = parseTitleInfo(doc);
        SeriesInfo seriesInfo = parseSeriesInfo(doc);

        return BookMetadata.builder()
                .provider(MetadataProvider.Amazon)
                .title(titleInfo.title())
                .subtitle(titleInfo.subtitle())
                .authors(new HashSet<>(getAuthors(doc)))
                .categories(new HashSet<>(getBestSellerCategories(doc)))
                .description(cleanDescriptionHtml(getDescription(doc)))
                .seriesName(seriesInfo.name())
                .seriesNumber(seriesInfo.number())
                .seriesTotal(seriesInfo.total())
                .isbn13(getIsbn(doc, "isbn13"))
                .isbn10(getIsbn(doc, "isbn10"))
                .asin(amazonBookId)
                .publisher(getPublisher(doc))
                .publishedDate(getPublicationDate(doc))
                .language(getLanguage(doc))
                .pageCount(getPageCount(doc))
                .thumbnailUrl(getThumbnail(doc))
                .amazonRating(getRating(doc))
                .amazonReviewCount(getReviewCount(doc))
                .bookReviews(reviews)
                .build();
    }

    private String buildQueryUrl(FetchMetadataRequest fetchMetadataRequest, Book book) {
        String domain = appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getDomain();
        String isbnCleaned = ParserUtils.cleanIsbn(fetchMetadataRequest.getIsbn());
        if (isbnCleaned != null && !isbnCleaned.isEmpty()) {
            String url = "https://www.amazon." + domain + "/s?k=" + fetchMetadataRequest.getIsbn();
            log.info("Amazon Query URL (ISBN): {}", url);
            return url;
        }

        StringBuilder searchTerm = new StringBuilder(256);

        String title = fetchMetadataRequest.getTitle();
        if (title != null && !title.isEmpty()) {
            searchTerm.append(cleanSearchTerm(title));
        } else {
            String filename = BookUtils.cleanAndTruncateSearchTerm(BookUtils.cleanFileName(book.getFileName()));
            if (!filename.isEmpty()) {
                searchTerm.append(cleanSearchTerm(filename));
            }
        }

        String author = fetchMetadataRequest.getAuthor();
        if (author != null && !author.isEmpty()) {
            if (!searchTerm.isEmpty()) {
                searchTerm.append(" ");
            }
            searchTerm.append(cleanSearchTerm(author));
        }

        if (searchTerm.isEmpty()) {
            return null;
        }

        String encodedSearchTerm = searchTerm.toString().replace(" ", "+");
        String url = "https://www.amazon." + domain + "/s?k=" + encodedSearchTerm;
        log.info("Amazon Query URL: {}", url);
        return url;
    }

    private String cleanSearchTerm(String text) {
        return Arrays.stream(text.split(" "))
                .map(word -> NON_ALPHANUMERIC_PATTERN.matcher(word).replaceAll("").trim())
                .filter(word -> !word.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private String getTextBySelectors(Document doc, String... selectors) {
        for (String selector : selectors) {
            try {
                Element element = doc.selectFirst(selector);
                if (element != null && !element.text().isBlank()) {
                    return element.text().trim();
                }
            } catch (Exception e) {
                log.debug("Failed to extract text with selector '{}': {}", selector, e.getMessage());
            }
        }
        return null;
    }

    private TitleInfo parseTitleInfo(Document doc) {
        String fullTitle = getTextBySelectors(doc, TITLE_SELECTORS);
        if (fullTitle == null) {
            log.warn("Failed to parse title: No suitable element found.");
            return new TitleInfo(null, null);
        }
        String[] parts = fullTitle.split(":", 2);
        String title = parts[0].trim();
        String subtitle = parts.length > 1 ? parts[1].trim() : null;
        return new TitleInfo(title, subtitle);
    }

    private Set<String> getAuthors(Document doc) {
        Set<String> authors = new HashSet<>();
        try {
            Element bylineDiv = doc.selectFirst("#bylineInfo_feature_div");
            if (bylineDiv != null) {
                authors.addAll(bylineDiv.select(".author a").stream().map(Element::text).collect(Collectors.toSet()));
            }

            if (authors.isEmpty()) {
                Element bylineInfo = doc.selectFirst("#bylineInfo");
                if (bylineInfo != null) {
                    authors.addAll(bylineInfo.select(".author a").stream().map(Element::text).collect(Collectors.toSet()));
                }
            }

            if (authors.isEmpty()) {
                authors.addAll(doc.select(".author a").stream().map(Element::text).collect(Collectors.toSet()));
            }

            if (authors.isEmpty()) {
                log.warn("Failed to parse authors: No author elements found.");
            }
        } catch (Exception e) {
            log.warn("Failed to parse authors: {}", e.getMessage());
        }
        return authors;
    }

    private String getDescription(Document doc) {
        try {
            Elements descriptionElements = doc.select("[data-a-expander-name=book_description_expander] .a-expander-content");
            if (!descriptionElements.isEmpty()) {
                String html = descriptionElements.getFirst().html();
                html = html.replace("\n", "<br>");
                return html;
            }

            Element noscriptDesc = doc.selectFirst("#bookDescription_feature_div noscript");
            if (noscriptDesc != null) {
                return noscriptDesc.html();
            }

            Element simpleDesc = doc.selectFirst("div.product-description");
            if (simpleDesc != null) {
                return simpleDesc.html();
            }

        } catch (Exception e) {
            log.warn("Failed to parse description: {}", e.getMessage());
        }
        return null;
    }

    private String getIsbn(Document doc, String type) {
        String rpiSelector = "#rpi-attribute-book_details-" + type + " .rpi-attribute-value span";
        String bulletKey = type.equals("isbn10") ? "ISBN-10" : "ISBN-13";

        try {
            Element isbnElement = doc.select(rpiSelector).first();
            if (isbnElement != null) {
                return ParserUtils.cleanIsbn(isbnElement.text());
            }
        } catch (Exception e) {
            log.debug("RPI {} extraction failed: {}", type.toUpperCase(), e.getMessage());
        }

        try {
            return extractFromDetailBullets(doc, bulletKey);
        } catch (Exception e) {
            log.warn("DetailBullets {} extraction failed: {}", type.toUpperCase(), e.getMessage());
        }
        return null;
    }

    private String getPublisher(Document doc) {
        try {
            Element featureElement = doc.getElementById("detailBullets_feature_div");
            if (featureElement != null) {
                Elements listItems = featureElement.select("li");
                for (Element listItem : listItems) {
                    Element boldText = listItem.selectFirst("span.a-text-bold");
                    if (boldText != null) {
                        String header = boldText.text().toLowerCase();
                        if (header.contains("publisher") ||
                            header.contains("herausgeber") || 
                            header.contains("éditeur") || 
                            header.contains("editoriale") || 
                            header.contains("editorial") || 
                            header.contains("uitgever") || 
                            header.contains("wydawca") ||
                            header.contains("出版社") ||
                            header.contains("editora")) {
                            
                            Element publisherSpan = boldText.nextElementSibling();
                            if (publisherSpan != null) {
                                String fullPublisher = publisherSpan.text().trim();
                                return PARENTHESES_WITH_WHITESPACE_PATTERN.matcher(fullPublisher.split(";")[0].trim()).replaceAll("").trim();
                            }
                        }
                    }
                }
            } else {
                log.debug("Failed to parse publisher: Element 'detailBullets_feature_div' not found.");
            }
        } catch (Exception e) {
            log.warn("Failed to parse publisher: {}", e.getMessage());
        }
        return null;
    }

    private LocalDate getPublicationDate(Document doc) {
        try {
            Element publicationDateElement = doc.select("#rpi-attribute-book_details-publication_date .rpi-attribute-value span").first();
            if (publicationDateElement != null) {
                String dateText = publicationDateElement.text();
                LocalDate parsedDate = parseDate(dateText);
                if (parsedDate != null) return parsedDate;
            }
        } catch (Exception e) {
            log.debug("RPI Publication Date extraction failed: {}", e.getMessage());
        }

        try {
            Element featureElement = doc.getElementById("detailBullets_feature_div");
            if (featureElement != null) {
                Elements listItems = featureElement.select("li");
                for (Element listItem : listItems) {
                    Element boldText = listItem.selectFirst("span.a-text-bold");
                    Element valueSpan = boldText != null ? boldText.nextElementSibling() : null;
                    
                    if (valueSpan != null) {
                         LocalDate d = parseDate(valueSpan.text());
                         if (d != null) return d;

                         Matcher matcher = Pattern.compile("\\((.*?)\\)").matcher(valueSpan.text());
                         while (matcher.find()) {
                             LocalDate pd = parseDate(matcher.group(1));
                             if (pd != null) return pd;
                         }
                    }
                }
            }
        } catch (Exception e) {
             log.warn("DetailBullets Publication Date extraction failed: {}", e.getMessage());
        }
        
        return null;
    }

    private String extractFromDetailBullets(Document doc, String keyPart) {
        Element featureElement = doc.getElementById("detailBullets_feature_div");
        if (featureElement != null) {
            Elements listItems = featureElement.select("li");
            for (Element listItem : listItems) {
                Element boldText = listItem.selectFirst("span.a-text-bold");
                if (boldText != null && boldText.text().contains(keyPart)) {
                    Element valueSpan = boldText.nextElementSibling();
                    if (valueSpan != null) {
                        return ParserUtils.cleanIsbn(valueSpan.text());
                    }
                }
            }
        }
        return null;
    }

    private SeriesInfo parseSeriesInfo(Document doc) {
        String name = null;
        Float number = null;
        Integer total = null;

        try {
            Element seriesNameElement = doc.selectFirst("#rpi-attribute-book_details-series .rpi-attribute-value a span");
            if (seriesNameElement != null) {
                name = seriesNameElement.text();
            }

            Element bookDetailsLabel = doc.selectFirst("#rpi-attribute-book_details-series .rpi-attribute-label span");
            if (bookDetailsLabel != null) {
                String bookAndTotal = bookDetailsLabel.text();
                Matcher matcher = SERIES_FORMAT_PATTERN.matcher(bookAndTotal);
                if (matcher.find()) {
                    number = Float.parseFloat(matcher.group(1));
                    total = Integer.parseInt(matcher.group(2));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse series info: {}", e.getMessage());
        }

        return new SeriesInfo(name, number, total);
    }

    private String getLanguage(Document doc) {
        try {
            Element languageElement = doc.select("#rpi-attribute-language .rpi-attribute-value span").first();
            if (languageElement != null) {
                return languageElement.text();
            }
            log.debug("Failed to parse language: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse language: {}", e.getMessage());
        }
        return null;
    }

    private Set<String> getBestSellerCategories(Document doc) {
        try {
            Element bestSellerCategoriesElement = doc.select("#detailBullets_feature_div").first();
            if (bestSellerCategoriesElement != null) {
                return bestSellerCategoriesElement
                        .select(".zg_hrsr .a-list-item a")
                        .stream()
                        .map(Element::text)
                        .map(c -> c.replace("(Books)", "").trim())
                        .collect(Collectors.toSet());
            }
            log.warn("Failed to parse categories: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse categories: {}", e.getMessage());
        }
        return Set.of();
    }

    private Double getRating(Document doc) {
        try {
            Element reviewDiv = doc.selectFirst("div#averageCustomerReviews_feature_div");
            if (reviewDiv != null) {
                Element ratingSpan = reviewDiv.selectFirst("span#acrPopover span.a-size-base.a-color-base");
                if (ratingSpan == null) {
                    ratingSpan = reviewDiv.selectFirst("span#acrPopover span.a-size-small.a-color-base");
                }
                if (ratingSpan != null) {
                    String text = ratingSpan.text().trim();
                    if (!text.isEmpty()) {
                        String normalizedText = text.replace(',', '.');
                        return Double.parseDouble(normalizedText);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse amazonRating: {}", e.getMessage());
        }
        return null;
    }

    private List<BookReview> getReviews(Document doc, int maxReviews) {
        List<BookReview> reviews = new ArrayList<>();
        String domain = appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getDomain();
        LocaleInfo localeInfo = getLocaleInfoForDomain(domain);

        try {
            Elements reviewElements = doc.select("li[data-hook=review]");
            int count = 0;
            int index = 0;

            while (count < maxReviews && index < reviewElements.size()) {
                Element reviewElement = reviewElements.get(index);
                index++;

                Elements reviewerNameElements = reviewElement.select(".a-profile-name");
                String reviewerName = !reviewerNameElements.isEmpty() ? reviewerNameElements.first().text() : null;

                String title = null;
                Elements titleElements = reviewElement.select("[data-hook=review-title] span");
                if (!titleElements.isEmpty()) {
                    title = titleElements.last().text();
                    if (title.isEmpty()) title = null;
                }

                Float ratingValue = null;
                Elements ratingElements = reviewElement.select("[data-hook=review-star-rating] .a-icon-alt");
                String ratingText = !ratingElements.isEmpty() ? ratingElements.first().text() : "";
                if (!ratingText.isEmpty()) {
                    try {
                        Pattern ratingPattern = Pattern.compile("^([0-9]+([.,][0-9]+)?)");
                        Matcher ratingMatcher = ratingPattern.matcher(ratingText);
                        if (ratingMatcher.find()) {
                            String ratingStr = ratingMatcher.group(1).replace(',', '.');
                            ratingValue = Float.parseFloat(ratingStr);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse rating '{}': {}", ratingText, e.getMessage());
                    }
                }

                Elements fullDateElements = reviewElement.select("[data-hook=review-date]");
                String fullDateText = !fullDateElements.isEmpty() ? fullDateElements.first().text() : "";
                String country = null;
                Instant dateInstant = null;

                if (!fullDateText.isEmpty()) {
                    try {
                        Matcher matcher = REVIEWED_IN_ON_PATTERN.matcher(fullDateText);
                        String datePart = fullDateText;

                        if (matcher.find() && matcher.groupCount() == 2) {
                            country = matcher.group(1).trim();
                            if (country.toLowerCase().startsWith("the ")) {
                                country = country.substring(4).trim();
                            }
                            datePart = matcher.group(2).trim();
                        } else {
                            Matcher japaneseMatcher = JAPANESE_REVIEW_DATE_PATTERN.matcher(fullDateText);
                            if (japaneseMatcher.find()) {
                                datePart = japaneseMatcher.group(1);
                                country = "日本"; 
                            }
                        }

                        LocalDate localDate = parseDate(datePart, localeInfo);
                        if (localDate != null) {
                            dateInstant = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                        }

                    } catch (Exception e) {
                        log.warn("Error parsing date string '{}': {}", fullDateText, e.getMessage());
                    }
                }

                Elements bodyElements = reviewElement.select("[data-hook=review-body]");
                String body = !bodyElements.isEmpty() ? Objects.requireNonNull(bodyElements.first()).text() : null;
                if (body != null && body.isEmpty()) {
                    body = null;
                } else if (body != null) {
                    String toRemove = " Read more";
                    int lastIndex = body.lastIndexOf(toRemove);
                    if (lastIndex != -1) {
                        body = body.substring(0, lastIndex) + body.substring(lastIndex + toRemove.length());
                    }
                }

                if (body == null) {
                    continue;
                }

                reviews.add(BookReview.builder()
                        .metadataProvider(MetadataProvider.Amazon)
                        .reviewerName(reviewerName != null ? reviewerName.trim() : null)
                        .title(title != null ? title.trim() : null)
                        .rating(ratingValue)
                        .country(country != null ? country.trim() : null)
                        .date(dateInstant)
                        .body(body.trim())
                        .build());

                count++;
            }
        } catch (Exception e) {
            log.warn("Failed to parse reviews: {}", e.getMessage());
        }
        return reviews;
    }

    private Integer getReviewCount(Document doc) {
        try {
            Element reviewDiv = doc.select("div#averageCustomerReviews_feature_div").first();
            if (reviewDiv != null) {
                Element reviewCountElement = reviewDiv.getElementById("acrCustomerReviewText");
                if (reviewCountElement != null) {
                    String reviewCountRaw = reviewCountElement.text().split(" ")[0];
                    String reviewCountClean = NON_DIGIT_PATTERN.matcher(reviewCountRaw).replaceAll("");
                    if (!reviewCountClean.isEmpty()) {
                        return Integer.parseInt(reviewCountClean);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse amazonReviewCount: {}", e.getMessage());
        }
        return null;
    }

    private String getThumbnail(Document doc) {
        try {
            Element imageElement = doc.selectFirst("#landingImage");
            if (imageElement != null) {
                String highRes = imageElement.attr("data-old-hires");
                if (!highRes.isBlank()) {
                    return highRes;
                }
                String fallback = imageElement.attr("src");
                if (!fallback.isBlank()) {
                    return fallback;
                }
            }
            log.warn("Failed to parse thumbnail: No suitable image URL found.");
        } catch (Exception e) {
            log.warn("Failed to parse thumbnail: {}", e.getMessage());
        }
        return null;
    }

    private Integer getPageCount(Document doc) {
        Elements pageCountElements = doc.select("#rpi-attribute-book_details-fiona_pages .rpi-attribute-value span");
        if (!pageCountElements.isEmpty()) {
            String pageCountText = pageCountElements.first().text();
            if (!pageCountText.isEmpty()) {
                try {
                    String cleanedPageCount = NON_DIGIT_PATTERN.matcher(pageCountText).replaceAll("");
                    return Integer.parseInt(cleanedPageCount);
                } catch (NumberFormatException e) {
                    log.warn("Error parsing page count: {}, error: {}", pageCountText, e.getMessage());
                }
            }
        }
        return null;
    }

    private Document fetchDocument(String url) {
        try {
            String domain = appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getDomain();
            String amazonCookie = appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getCookie();

            LocaleInfo localeInfo = getLocaleInfoForDomain(domain);

            Connection connection = Jsoup.connect(url)
                    .header("accept", "text/html, application/json")
                    .header("accept-language", localeInfo.acceptLanguage)
                    .header("content-type", "application/json")
                    .header("device-memory", "8")
                    .header("downlink", "10")
                    .header("dpr", "2")
                    .header("ect", "4g")
                    .header("origin", "https://www.amazon." + domain)
                    .header("priority", "u=1, i")
                    .header("rtt", "50")
                    .header("sec-ch-device-memory", "8")
                    .header("sec-ch-dpr", "2")
                    .header("sec-ch-ua", "\"Google Chrome\";v=\"137\", \"Chromium\";v=\"137\", \"Not_A Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"macOS\"")
                    .header("sec-ch-viewport-width", "1170")
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-origin")
                    .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
                    .header("viewport-width", "1170")
                    .header("x-amz-amabot-click-attributes", "disable")
                    .header("x-requested-with", "XMLHttpRequest")
                    .method(Connection.Method.GET);

            if (amazonCookie != null && !amazonCookie.isBlank()) {
                connection.header("cookie", amazonCookie);
            }

            Connection.Response response = connection.execute();
            return response.parse();
        } catch (IOException e) {
            log.error("Error parsing url: {}", url, e);
            throw new RuntimeException(e);
        }
    }

    private static LocaleInfo getLocaleInfoForDomain(String domain) {
        return DOMAIN_LOCALE_MAP.getOrDefault(domain, DEFAULT_LOCALE_INFO);
    }

    private static LocalDate parseDate(String dateString, LocaleInfo localeInfo) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        String trimmedDate = dateString.trim();

        for (String pattern : DATE_PATTERNS) {
            try {
                return LocalDate.parse(trimmedDate, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
            }
        }

        if (!"en".equals(localeInfo.locale().getLanguage())) {
            for (String pattern : DATE_PATTERNS) {
                try {
                    return LocalDate.parse(trimmedDate, DateTimeFormatter.ofPattern(pattern, localeInfo.locale()));
                } catch (DateTimeParseException ignored) {
                }
            }
        }

        log.warn("Failed to parse date '{}' with any known format for locale {}", dateString, localeInfo.locale());
        return null;
    }

    private LocalDate parseDate(String dateString) {
        String domain = appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getDomain();
        return parseDate(dateString, getLocaleInfoForDomain(domain));
    }

    private String cleanDescriptionHtml(String html) {
        try {
            Document document = Jsoup.parse(html);
            document.select("span.a-text-bold").tagName("b").removeAttr("class");
            document.select("span.a-text-italic").tagName("i").removeAttr("class");
            for (Element span : document.select("span.a-list-item")) {
                span.unwrap();
            }
            document.select("ol.a-ordered-list.a-vertical").tagName("ol").removeAttr("class");
            document.select("ul.a-unordered-list.a-vertical").tagName("ul").removeAttr("class");
            for (Element span : document.select("span")) {
                span.unwrap();
            }
            document.select("li").forEach(li -> {
                Element prev = li.previousElementSibling();
                if (prev != null && "br".equals(prev.tagName())) {
                    prev.remove();
                }
                Element next = li.nextElementSibling();
                if (next != null && "br".equals(next.tagName())) {
                    next.remove();
                }
            });
            document.select("p").stream()
                    .filter(p -> p.text().trim().isEmpty())
                    .forEach(Element::remove);

            // Remove excessive line breaks (more than 2 consecutive <br> tags)
            Elements brTags = document.select("br");
            for (int i = 0; i < brTags.size(); i++) {
                Element br = brTags.get(i);
                int consecutiveBrCount = 1;
                Element next = br.nextElementSibling();

                // Count consecutive <br> tags
                while (next != null && "br".equals(next.tagName())) {
                    consecutiveBrCount++;
                    Element temp = next;
                    next = next.nextElementSibling();

                    // Remove extra <br> tags beyond the first two
                    if (consecutiveBrCount > 2) {
                        temp.remove();
                    }
                }
            }

            // Clean up any remaining whitespace issues
            String cleanedHtml = document.body().html();

            // Replace multiple consecutive <br> patterns that might still exist
            cleanedHtml = cleanedHtml.replaceAll("(<br>\\s*){3,}", "<br><br>");
            cleanedHtml = cleanedHtml.replaceAll("(<br\\s*/?>\\s*){3,}", "<br><br>");

            // Remove leading/trailing <br> tags
            cleanedHtml = cleanedHtml.replaceAll("^(\\s*<br\\s*/?>\\s*)+", "");
            cleanedHtml = cleanedHtml.replaceAll("(\\s*<br\\s*/?>\\s*)+$", "");

            return cleanedHtml;
        } catch (Exception e) {
            log.warn("Error cleaning html description, Error: {}", e.getMessage());
        }
        return html;
    }
}
