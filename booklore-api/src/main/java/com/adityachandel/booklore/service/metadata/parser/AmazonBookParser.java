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
    private static final String BASE_BOOK_URL = "https://www.amazon.com/dp/";
    private final AppSettingService appSettingService;

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
        String[] parts = url.split("/dp/");
        if (parts.length > 1) {
            String[] asinParts = parts[1].split("/");
            return asinParts[0];
        }
        return null;
    }

    private BookMetadata getBookMetadata(String amazonBookId) {
        log.info("Amazon: Fetching metadata for: {}", amazonBookId);

        Document doc = fetchDocument(BASE_BOOK_URL + amazonBookId);

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
        return BookMetadata.builder()
                .provider(MetadataProvider.Amazon)
                .title(getTitle(doc))
                .subtitle(getSubtitle(doc))
                .authors(new HashSet<>(getAuthors(doc)))
                .categories(new HashSet<>(getBestSellerCategories(doc)))
                .description(cleanDescriptionHtml(getDescription(doc)))
                .seriesName(getSeriesName(doc))
                .seriesNumber(getSeriesNumber(doc))
                .seriesTotal(getSeriesTotal(doc))
                .isbn13(getIsbn13(doc))
                .isbn10(getIsbn10(doc))
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
        // 1. Prefer ISBN if present
        if (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isEmpty()) {
            String url = "https://www.amazon."
                    + appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getDomain()
                    + "/s?k=" + fetchMetadataRequest.getIsbn();
            log.info("Amazon Query URL (ISBN): {}", url);
            return url;
        }

        // 2. Otherwise, fall back to title + author + filename
        StringBuilder searchTerm = new StringBuilder();

        String title = fetchMetadataRequest.getTitle();
        if (title != null && !title.isEmpty()) {
            String cleanedTitle = Arrays.stream(title.split(" "))
                    .map(word -> word.replaceAll("[^a-zA-Z0-9]", "").trim())
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.joining(" "));
            searchTerm.append(cleanedTitle);
        } else {
            String filename = BookUtils.cleanAndTruncateSearchTerm(BookUtils.cleanFileName(book.getFileName()));
            if (!filename.isEmpty()) {
                String cleanedFilename = Arrays.stream(filename.split(" "))
                        .map(word -> word.replaceAll("[^a-zA-Z0-9]", "").trim())
                        .filter(word -> !word.isEmpty())
                        .collect(Collectors.joining(" "));
                searchTerm.append(cleanedFilename);
            }
        }

        String author = fetchMetadataRequest.getAuthor();
        if (author != null && !author.isEmpty()) {
            if (!searchTerm.isEmpty()) {
                searchTerm.append(" ");
            }
            String cleanedAuthor = Arrays.stream(author.split(" "))
                    .map(word -> word.replaceAll("[^a-zA-Z0-9]", "").trim())
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.joining(" "));
            searchTerm.append(cleanedAuthor);
        }

        if (searchTerm.isEmpty()) {
            return null;
        }

        String encodedSearchTerm = searchTerm.toString().replace(" ", "+");
        String url = "https://www.amazon."
                + appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getDomain()
                + "/s?k=" + encodedSearchTerm;
        log.info("Amazon Query URL: {}", url);
        return url;
    }

    private String getTitle(Document doc) {
        Element titleElement = doc.getElementById("productTitle");
        if (titleElement != null) {
            return titleElement.text();
        }
        log.warn("Failed to parse title: Element not found.");
        return null;
    }

    private String getSubtitle(Document doc) {
        Element subtitleElement = doc.getElementById("productSubtitle");
        if (subtitleElement != null) {
            return subtitleElement.text();
        }
        log.warn("Failed to parse subtitle: Element not found.");
        return null;
    }

    private Set<String> getAuthors(Document doc) {
        try {
            Element bylineDiv = doc.select("#bylineInfo_feature_div").first();
            if (bylineDiv != null) {
                return bylineDiv
                        .select(".author a")
                        .stream()
                        .map(Element::text)
                        .collect(Collectors.toSet());
            }
            log.warn("Failed to parse authors: Byline element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse authors: {}", e.getMessage());
        }
        return Set.of();
    }

    private String getDescription(Document doc) {
        try {
            Elements descriptionElements = doc.select("[data-a-expander-name=book_description_expander] .a-expander-content");
            if (!descriptionElements.isEmpty()) {
                String html = descriptionElements.getFirst().html();
                html = html.replace("\n", "<br>");
                return html;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse description: {}", e.getMessage());
        }
        return null;
    }

    private String getIsbn10(Document doc) {
        try {
            Element isbn10Element = doc.select("#rpi-attribute-book_details-isbn10 .rpi-attribute-value span").first();
            if (isbn10Element != null) {
                return isbn10Element.text();
            }
            log.warn("Failed to parse isbn10: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse isbn10: {}", e.getMessage());
        }
        return null;
    }

    private String getIsbn13(Document doc) {
        try {
            Element isbn13Element = doc.select("#rpi-attribute-book_details-isbn13 .rpi-attribute-value span").first();
            if (isbn13Element != null) {
                return isbn13Element.text();
            }
            log.warn("Failed to parse isbn13: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse isbn13: {}", e.getMessage());
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
                    if (boldText != null && boldText.text().contains("Publisher")) {
                        Element publisherSpan = boldText.nextElementSibling();
                        if (publisherSpan != null) {
                            String fullPublisher = publisherSpan.text().trim();
                            return fullPublisher.split(";")[0].trim().replaceAll("\\s*\\(.*?\\)", "").trim();
                        }
                    }
                }
            } else {
                log.warn("Failed to parse publisher: Element 'detailBullets_feature_div' not found.");
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
                return parseAmazonDate(publicationDateElement.text());
            }
            log.warn("Failed to parse publishedDate: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse publishedDate: {}", e.getMessage());
        }
        return null;
    }

    private String getSeriesName(Document doc) {
        try {
            Element seriesNameElement = doc.selectFirst("#rpi-attribute-book_details-series .rpi-attribute-value a span");
            if (seriesNameElement != null) {
                return seriesNameElement.text();
            } else {
                log.debug("Failed to parse seriesName: Element not found.");
            }
        } catch (Exception e) {
            log.warn("Failed to parse seriesName: {}", e.getMessage());
        }
        return null;
    }

    private Float getSeriesNumber(Document doc) {
        try {
            Element bookDetailsLabel = doc.selectFirst("#rpi-attribute-book_details-series .rpi-attribute-label span");
            if (bookDetailsLabel != null) {
                String bookAndTotal = bookDetailsLabel.text();
                if (bookAndTotal.matches("Book \\d+(\\.\\d+)? of \\d+")) {
                    String[] parts = bookAndTotal.split(" ");
                    return Float.parseFloat(parts[1]);
                }
            } else {
                log.debug("Failed to parse seriesNumber: Element not found.");
            }
        } catch (Exception e) {
            log.warn("Failed to parse seriesNumber: {}", e.getMessage());
        }
        return null;
    }

    private Integer getSeriesTotal(Document doc) {
        try {
            Element bookDetailsLabel = doc.selectFirst("#rpi-attribute-book_details-series .rpi-attribute-label span");
            if (bookDetailsLabel != null) {
                String bookAndTotal = bookDetailsLabel.text();
                if (bookAndTotal.matches("Book \\d+ of \\d+")) {
                    String[] parts = bookAndTotal.split(" ");
                    return Integer.parseInt(parts[3]);
                }
            } else {
                log.debug("Failed to parse seriesTotal: Element not found.");
            }
        } catch (Exception e) {
            log.warn("Failed to parse seriesTotal: {}", e.getMessage());
        }
        return null;
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
                        return Double.parseDouble(text);
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

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
                        Pattern ratingPattern = Pattern.compile("^([0-9]+(\\.[0-9]+)?)");
                        Matcher ratingMatcher = ratingPattern.matcher(ratingText);
                        if (ratingMatcher.find()) {
                            ratingValue = Float.parseFloat(ratingMatcher.group(1));
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
                        Pattern pattern = Pattern.compile("Reviewed in (.+?) on (.+)");
                        Matcher matcher = pattern.matcher(fullDateText);
                        String datePart = fullDateText;

                        if (matcher.find() && matcher.groupCount() == 2) {
                            country = matcher.group(1).trim();
                            if (country.toLowerCase().startsWith("the ")) {
                                country = country.substring(4).trim();
                            }
                            datePart = matcher.group(2).trim();
                        }

                        LocalDate localDate = LocalDate.parse(datePart, formatter);
                        dateInstant = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();

                    } catch (DateTimeParseException e) {
                        log.warn("Failed to parse date '{}' in review: {}", fullDateText, e.getMessage());
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
                    String reviewCountClean = reviewCountRaw.replaceAll("[^\\d]", "");
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
                    String cleanedPageCount = pageCountText.replaceAll("[^\\d]", "");
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
            String amazonCookie = appSettingService.getAppSettings().getMetadataProviderSettings().getAmazon().getCookie();
            Connection connection = Jsoup.connect(url)
                    .header("accept", "text/html, application/json")
                    .header("accept-language", "en-US,en;q=0.9")
                    .header("content-type", "application/json")
                    .header("device-memory", "8")
                    .header("downlink", "10")
                    .header("dpr", "2")
                    .header("ect", "4g")
                    .header("origin", "https://www.amazon.com")
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

    private LocalDate parseAmazonDate(String dateString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy");
        return LocalDate.parse(dateString, formatter);
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
            return document.body().html();
        } catch (Exception e) {
            log.warn("Error cleaning html description, Error: {}", e.getMessage());
        }
        return html;
    }
}