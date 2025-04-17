package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class AmazonBookParser implements BookParser {

    private static final int COUNT_DETAILED_METADATA_TO_GET = 3;
    private static final String BASE_SEARCH_URL = "https://www.amazon.com/s/?search-alias=stripbooks&unfiltered=1&sort=relevanceexprank";
    private static final String BASE_BOOK_URL = "https://www.amazon.com/dp/";

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
            fetchedBookMetadata.add(getBookMetadata(amazonBookId));
        }
        return fetchedBookMetadata;
    }

    private LinkedList<String> getAmazonBookIds(Book book, FetchMetadataRequest request) {
        log.info("Amazon: Querying metadata for ISBN: {}, Title: {}, Author: {}, FileName: {}", request.getIsbn(), request.getTitle(), request.getAuthor(), book.getFileName());
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
                //log.info("{} link found: {}", type, bookLink);
                break; // Take the first found link, whether Paperback or Hardcover
            } else {
                //log.info("No link containing '{}' found.", type);
            }
        }
        if (bookLink != null) {
            return extractAsinFromUrl(bookLink);
        } else {
            String asin = item.attr("data-asin");
            //log.info("No book link found, returning ASIN: {}", asin);
            return asin;
        }
    }

    private String extractAsinFromUrl(String url) {
        // Extract the ASIN (book ID) from the URL, which will be the part after "/dp/"
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
        return BookMetadata.builder()
                .providerBookId(amazonBookId)
                .provider(MetadataProvider.Amazon)
                .title(getTitle(doc))
                .subtitle(getSubtitle(doc))
                .authors(getAuthors(doc).stream().toList())
                .categories(getBestSellerCategories(doc).stream().toList())
                .description(cleanDescriptionHtml(getDescription(doc)))
                .seriesName(getSeriesName(doc))
                .seriesNumber(getSeriesNumber(doc))
                .seriesTotal(getSeriesTotal(doc))
                .isbn13(getIsbn13(doc))
                .isbn10(getIsbn10(doc))
                .publisher(getPublisher(doc))
                .publishedDate(getPublicationDate(doc))
                .language(getLanguage(doc))
                .pageCount(getPageCount(doc))
                .thumbnailUrl(getThumbnail(doc))
                .rating(getRating(doc))
                .reviewCount(getReviewCount(doc))
                .build();
    }

    private String buildQueryUrl(FetchMetadataRequest fetchMetadataRequest, Book book) {
        StringBuilder queryBuilder = new StringBuilder(BASE_SEARCH_URL);

        /*// Always add ISBN if present
        if (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isEmpty()) {
            queryBuilder.append("&field-isbn=").append(fetchMetadataRequest.getIsbn());
        }*/

        // Add title if present, otherwise check filename if title is absent
        String title = fetchMetadataRequest.getTitle();
        if (title != null && !title.isEmpty()) {
            title = BookUtils.cleanAndTruncateSearchTerm(title);
            queryBuilder.append("&field-title=").append(title.replace(" ", "%20"));
        } else {
            String filename = BookUtils.cleanAndTruncateSearchTerm(BookUtils.cleanFileName(book.getFileName()));
            if (filename != null && !filename.isEmpty()) {
                queryBuilder.append("&field-title=").append(filename.replace(" ", "%20"));
            }
        }

        // Add author only if title or filename is present
        if ((title != null && !title.isEmpty()) || (fetchMetadataRequest.getIsbn() != null && !fetchMetadataRequest.getIsbn().isEmpty())) {
            String author = fetchMetadataRequest.getAuthor();
            if (author != null && !author.isEmpty()) {
                queryBuilder.append("&field-author=").append(author.replace(" ", "%20"));
            }
        }

        // If ISBN, Title, or Filename is missing, return null
        if (fetchMetadataRequest.getIsbn() == null && (title == null || title.isEmpty()) && (book.getFileName() == null || book.getFileName().isEmpty())) {
            return null;
        }

        log.info("Query URL: {}", queryBuilder.toString());
        return queryBuilder.toString();
    }

    private String getTitle(Document doc) {
        Element titleElement = doc.getElementById("productTitle");
        if (titleElement != null) {
            return titleElement.text();
        }
        log.error("Error fetching title: Element not found.");
        return null;
    }

    private String getSubtitle(Document doc) {
        Element subtitleElement = doc.getElementById("productSubtitle");
        if (subtitleElement != null) {
            return subtitleElement.text();
        }
        log.debug("Error fetching subtitle: Element not found.");
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
            log.error("Error fetching authors: Byline element not found.");
        } catch (Exception e) {
            log.error("Error fetching authors: {}", e.getMessage());
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
            log.error("Error extracting description from the document", e);
        }
        return null;
    }

    private String getIsbn10(Document doc) {
        try {
            Element isbn10Element = doc.select("#rpi-attribute-book_details-isbn10 .rpi-attribute-value span").first();
            if (isbn10Element != null) {
                return isbn10Element.text();
            }
            log.debug("Error fetching ISBN-10: Element not found.");
        } catch (Exception e) {
            log.debug("Error fetching ISBN-10: {}", e.getMessage());
        }
        return null;
    }

    private String getIsbn13(Document doc) {
        try {
            Element isbn13Element = doc.select("#rpi-attribute-book_details-isbn13 .rpi-attribute-value span").first();
            if (isbn13Element != null) {
                return isbn13Element.text();
            }
            log.debug("Error fetching ISBN-13: Element not found.");
        } catch (Exception e) {
            log.debug("Error fetching ISBN-13: {}", e.getMessage());
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
                log.warn("Error fetching publisher: Element 'detailBullets_feature_div' not found.");
            }
        } catch (Exception e) {
            log.warn("Error fetching publisher: {}", e.getMessage());
        }
        return null;
    }

    private LocalDate getPublicationDate(Document doc) {
        try {
            Element publicationDateElement = doc.select("#rpi-attribute-book_details-publication_date .rpi-attribute-value span").first();
            if (publicationDateElement != null) {
                return parseAmazonDate(publicationDateElement.text());
            }
            log.warn("Error fetching publication date: Element not found.");
        } catch (Exception e) {
            log.warn("Error fetching publication date: {}", e.getMessage());
        }
        return null;
    }

    private String getSeriesName(Document doc) {
        try {
            Element seriesNameElement = doc.selectFirst("#rpi-attribute-book_details-series .rpi-attribute-value a span");
            if (seriesNameElement != null) {
                return seriesNameElement.text();
            } else {
                log.debug("Error fetching series name: Element not found.");
            }
        } catch (Exception e) {
            log.debug("Error fetching series name: {}", e.getMessage());
        }
        return null;
    }

    private Integer getSeriesNumber(Document doc) {
        try {
            Element bookDetailsLabel = doc.selectFirst("#rpi-attribute-book_details-series .rpi-attribute-label span");
            if (bookDetailsLabel != null) {
                String bookAndTotal = bookDetailsLabel.text();
                if (bookAndTotal.matches("Book \\d+ of \\d+")) {
                    String[] parts = bookAndTotal.split(" ");
                    return Integer.parseInt(parts[1]);
                }
            } else {
                log.debug("Error fetching series number: Element not found.");
            }
        } catch (Exception e) {
            log.debug("Error fetching series number: {}", e.getMessage());
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
                log.debug("Error fetching series total: Element not found.");
            }
        } catch (Exception e) {
            log.debug("Error fetching series total: {}", e.getMessage());
        }
        return null;
    }

    private String getLanguage(Document doc) {
        try {
            Element languageElement = doc.select("#rpi-attribute-language .rpi-attribute-value span").first();
            if (languageElement != null) {
                return languageElement.text();
            }
            log.warn("Error fetching language: Element not found.");
        } catch (Exception e) {
            log.warn("Error fetching language: {}", e.getMessage());
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
            log.warn("Error fetching best seller categories: Element not found.");
        } catch (Exception e) {
            log.warn("Error fetching best seller categories: {}", e.getMessage());
        }
        return Set.of();
    }

    private Double getRating(Document doc) {
        try {
            Element reviewDiv = doc.select("div#averageCustomerReviews_feature_div").first();
            if (reviewDiv != null) {
                Elements ratingElements = reviewDiv.select("span#acrPopover span.a-size-base.a-color-base");
                if (!ratingElements.isEmpty()) {
                    String text = Objects.requireNonNull(ratingElements.first()).text();
                    if (!text.isEmpty()) {
                        return Double.parseDouble(text);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching rating", e);
        }
        return null;
    }

    private Integer getReviewCount(Document doc) {
        try {
            Element reviewDiv = doc.select("div#averageCustomerReviews_feature_div").first();
            if (reviewDiv != null) {
                Element reviewCountElement = reviewDiv.getElementById("acrCustomerReviewText");
                if (reviewCountElement != null) {
                    String reviewCount = Objects.requireNonNull(reviewCountElement).text().split(" ")[0];
                    if (!reviewCount.isEmpty()) {
                        reviewCount = reviewCount.replace(",", "");
                        return Integer.parseInt(reviewCount);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching review count", e);
        }
        return null;
    }

    private String getThumbnail(Document doc) {
        try {
            Element imageElement = doc.select("#landingImage").first();
            if (imageElement != null) {
                return imageElement.attr("src");
            }
            log.warn("Error fetching image URL: Image element not found.");
        } catch (Exception e) {
            log.warn("Error fetching image URL: {}", e.getMessage());
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
                    log.warn("Error parsing page count: {}", pageCountText, e);
                }
            }
        }
        return null;
    }

    private Document fetchDocument(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
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
                    .header("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"macOS\"")
                    .header("sec-ch-viewport-width", "1170")
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-origin")
                    .header("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("viewport-width", "1170")
                    .header("x-amz-amabot-click-attributes", "disable")
                    .header("x-requested-with", "XMLHttpRequest")
                    .method(Connection.Method.GET)
                    .execute();
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
                span.unwrap();  // Removes the span and keeps its content
            }
            document.select("ol.a-ordered-list.a-vertical").tagName("ol").removeAttr("class");
            document.select("ul.a-unordered-list.a-vertical").tagName("ul").removeAttr("class");
            for (Element span : document.select("span")) {
                span.unwrap();
            }
            document.select("li").forEach(li -> {
                // Remove <br> tags preceding the <li> (if any)
                Element prev = li.previousElementSibling();
                if (prev != null && "br".equals(prev.tagName())) {
                    prev.remove();
                }

                // Remove <br> tags following the <li> (if any)
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
            log.warn("Error cleaning html description", e);
        }
        return html;
    }
}