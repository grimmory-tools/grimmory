package com.adityachandel.booklore.service.metadata.parser;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.model.dto.BookMetadata;
import com.adityachandel.booklore.model.dto.BookReview;
import com.adityachandel.booklore.model.dto.request.FetchMetadataRequest;
import com.adityachandel.booklore.model.enums.MetadataProvider;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.adityachandel.booklore.util.BookUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
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
public class DoubanBookParser implements BookParser {

    private static final int COUNT_DETAILED_METADATA_TO_GET = 3;
    private static final String BASE_BOOK_URL = "https://book.douban.com/subject/";
    private final AppSettingService appSettingService;

    @Override
    public BookMetadata fetchTopMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> searchResults = getDoubanSearchResults(book, fetchMetadataRequest);
        if (searchResults == null || searchResults.isEmpty()) {
            return null;
        }
        BookMetadata topResult = searchResults.get(0);

        // If Douban reviews are enabled, fetch detailed metadata including reviews
        if (topResult.getDoubanId() != null && areDoubanReviewsEnabled()) {
            BookMetadata detailedMetadata = getBookMetadata(topResult.getDoubanId());
            if (detailedMetadata != null) {
                // Merge detailed metadata with search result, preserving search result data where detailed is missing
                if (detailedMetadata.getThumbnailUrl() == null && topResult.getThumbnailUrl() != null) {
                    detailedMetadata.setThumbnailUrl(topResult.getThumbnailUrl());
                }
                if (detailedMetadata.getAuthors() == null || detailedMetadata.getAuthors().isEmpty()) {
                    detailedMetadata.setAuthors(topResult.getAuthors());
                }
                if (detailedMetadata.getPublisher() == null && topResult.getPublisher() != null) {
                    detailedMetadata.setPublisher(topResult.getPublisher());
                }
                if (detailedMetadata.getPublishedDate() == null && topResult.getPublishedDate() != null) {
                    detailedMetadata.setPublishedDate(topResult.getPublishedDate());
                }
                if (detailedMetadata.getDoubanRating() == null && topResult.getDoubanRating() != null) {
                    detailedMetadata.setDoubanRating(topResult.getDoubanRating());
                }
                return detailedMetadata;
            }
        }

        return topResult;
    }

    @Override
    public List<BookMetadata> fetchMetadata(Book book, FetchMetadataRequest fetchMetadataRequest) {
        List<BookMetadata> searchResults = getDoubanSearchResults(book, fetchMetadataRequest);
        if (searchResults == null || searchResults.isEmpty()) {
            return null;
        }

        // For detailed metadata, fetch full book information for top results
        List<BookMetadata> detailedMetadata = new ArrayList<>();
        int count = 0;
        for (BookMetadata searchResult : searchResults) {
            if (count >= COUNT_DETAILED_METADATA_TO_GET) {
                break;
            }
            if (searchResult.getDoubanId() != null) {
                BookMetadata fullMetadata = getBookMetadata(searchResult.getDoubanId());
                if (fullMetadata != null) {
                    // Preserve the cover URL from search results if full metadata doesn't have it
                    if (fullMetadata.getThumbnailUrl() == null && searchResult.getThumbnailUrl() != null) {
                        fullMetadata.setThumbnailUrl(searchResult.getThumbnailUrl());
                    }
                    detailedMetadata.add(fullMetadata);
                    count++;
                }
            }
        }
        return detailedMetadata;
    }

    private List<BookMetadata> getDoubanSearchResults(Book book, FetchMetadataRequest request) {
        log.debug("Douban: Querying metadata for ISBN: {}, Title: {}, Author: {}, FileName: {}", request.getIsbn(), request.getTitle(), request.getAuthor(), book.getFileName());
        String queryUrl = buildQueryUrl(request, book);
        if (queryUrl == null) {
            log.error("Query URL is null, cannot proceed.");
            return null;
        }
        List<BookMetadata> searchResults = new ArrayList<>();
        try {
            Document doc = fetchDocument(queryUrl);

            // Extract JSON data from window.__DATA__
            String htmlContent = doc.html();
            String jsonData = null;

            // Use regex to find the JSON object in window.__DATA__
            Pattern pattern = Pattern.compile("window\\.__DATA__\\s*=\\s*(\\{.*\\});", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(htmlContent);
            if (matcher.find()) {
                jsonData = matcher.group(1);
                log.debug("Successfully extracted JSON data, length: {}", jsonData.length());
            }

            if (jsonData == null) {
                log.warn("No JSON data found in Douban search response");
                return null;
            }

            log.debug("Extracted JSON data: {}", jsonData);

            // Parse JSON data
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(jsonData);
                log.debug("Successfully parsed JSON");
            } catch (Exception e) {
                log.warn("Failed to parse JSON: {}", e.getMessage());
                return null;
            }
            JsonNode itemsNode = rootNode.get("items");

            log.debug("Items node: {}", itemsNode != null ? itemsNode.toString() : "null");

            if (itemsNode == null || !itemsNode.isArray()) {
                log.warn("No items found in Douban search response or items is not an array");
                return null;
            }

            if (itemsNode.size() == 0) {
                log.info("No books found for the search query");
                return null;
            }

            for (JsonNode item : itemsNode) {
                try {
                    String title = item.get("title").asText();
                    String url = item.get("url").asText();
                    String coverUrl = item.get("cover_url").asText();
                    String doubanId = extractDoubanIdFromUrl(url);

                    // Extract abstract information
                    String abstractText = item.get("abstract").asText();
                    Set<String> authors = Set.of();
                    String publisher = null;
                    String pubDate = null;

                    if (abstractText != null && !abstractText.isEmpty()) {
                        // Parse abstract: "author0 / author1 / author 2 / ... / publisher / date (YYYY-MM or YYYY-MM-DD) / price"
                        String[] parts = abstractText.split(" / ");
                        if (parts.length >= 4) {
                            // Authors are all parts except the last three (publisher, date, price)
                            authors = Arrays.stream(parts, 0, parts.length - 3)
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty())
                                    .collect(Collectors.toSet());
                            publisher = parts[parts.length - 3].trim();
                            pubDate = parts[parts.length - 2].trim();
                        } else if (parts.length >= 2) {
                            // Fallback for shorter abstracts
                            authors = Set.of(parts[1].trim());
                            if (parts.length >= 3) {
                                publisher = parts[2].trim();
                            }
                            if (parts.length >= 4) {
                                pubDate = parts[3].trim();
                            }
                        }
                    }

                    // Extract rating information
                    JsonNode ratingNode = item.get("rating");
                    Double rating = null;
                    if (ratingNode != null && ratingNode.has("value")) {
                        rating = ratingNode.get("value").asDouble();
                    }

                    if (doubanId != null && !title.isEmpty()) {
                        BookMetadata metadata = BookMetadata.builder()
                                .provider(MetadataProvider.Douban)
                                .title(title)
                                .doubanId(doubanId)
                                .thumbnailUrl(coverUrl)
                                .publisher(publisher)
                                .doubanRating(rating)
                                .authors(authors)
                                .build();

                        // Try to parse publication date
                        if (pubDate != null) {
                            try {
                                metadata.setPublishedDate(parseDoubanDate(pubDate));
                            } catch (Exception e) {
                                log.debug("Could not parse publication date: {}", pubDate);
                            }
                        }

                        searchResults.add(metadata);
                        log.debug("Found book: {} with ID: {} and cover: {}", title, doubanId, coverUrl);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing search result item: {}", e.getMessage());
                    continue;
                }
            }

        } catch (Exception e) {
            log.warn("Failed to get Douban search results: {}", e.getMessage(), e);
        }
        log.info("Douban: Found {} search results", searchResults.size());
        return searchResults;
    }

    private String extractDoubanIdFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile("/subject/(\\d+)/");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private BookMetadata getBookMetadata(String doubanBookId) {
        log.debug("Douban: Fetching metadata for: {}", doubanBookId);

        Document doc = fetchDocument(BASE_BOOK_URL + doubanBookId);

        List<BookReview> reviews = appSettingService.getAppSettings()
                .getMetadataPublicReviewsSettings()
                .getProviders()
                .stream()
                .filter(cfg -> cfg.getProvider() == MetadataProvider.Douban && cfg.isEnabled())
                .findFirst()
                .map(cfg -> getReviews(doc, cfg.getMaxReviews()))
                .orElse(Collections.emptyList());

        return buildBookMetadata(doc, doubanBookId, reviews);
    }

    private BookMetadata buildBookMetadata(Document doc, String doubanBookId, List<BookReview> reviews) {
        return BookMetadata.builder()
                .provider(MetadataProvider.Douban)
                .title(getTitle(doc))
                .subtitle(getSubtitle(doc))
                .authors(new HashSet<>(getAuthors(doc)))
                .categories(new HashSet<>(getCategories(doc)))
                .description(cleanDescriptionHtml(getDescription(doc)))
                .seriesName(getSeriesName(doc))
                .seriesNumber(getSeriesNumber(doc))
                .seriesTotal(getSeriesTotal(doc))
                .isbn13(getIsbn13(doc))
                .isbn10(getIsbn10(doc))
                .doubanId(doubanBookId)
                .publisher(getPublisher(doc))
                .publishedDate(getPublicationDate(doc))
                .language(getLanguage(doc))
                .pageCount(getPageCount(doc))
                .thumbnailUrl(getThumbnail(doc))
                .doubanRating(getRating(doc))
                .doubanReviewCount(getReviewCount(doc))
                .bookReviews(reviews)
                .build();
    }

    private String buildQueryUrl(FetchMetadataRequest fetchMetadataRequest, Book book) {
        StringBuilder searchTerm = new StringBuilder();

        String title = fetchMetadataRequest.getTitle();
        if (title != null && !title.isEmpty()) {
            String cleanedTitle = Arrays.stream(title.split(" "))
                    .map(word -> word.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]", "").trim())
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.joining(" "));
            searchTerm.append(cleanedTitle);
        } else {
            String filename = BookUtils.cleanAndTruncateSearchTerm(BookUtils.cleanFileName(book.getFileName()));
            if (!filename.isEmpty()) {
                String cleanedFilename = Arrays.stream(filename.split(" "))
                        .map(word -> word.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]", "").trim())
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
                    .map(word -> word.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]", "").trim())
                    .filter(word -> !word.isEmpty())
                    .collect(Collectors.joining(" "));
            searchTerm.append(cleanedAuthor);
        }

        if (searchTerm.isEmpty()) {
            return null;
        }

        String encodedSearchTerm = searchTerm.toString().replace(" ", "+");
        String url = "https://search.douban.com/book/subject_search?search_text=" + encodedSearchTerm;
        log.info("Douban Query URL: {}", url);
        return url;
    }

    private String getTitle(Document doc) {
        Element titleElement = doc.getElementById("wrapper");
        if (titleElement != null) {
            Element h1 = titleElement.selectFirst("h1");
            if (h1 != null) {
                return h1.text().trim();
            }
        }
        log.warn("Failed to parse title: Element not found.");
        return null;
    }

    private String getSubtitle(Document doc) {
        // Douban doesn't typically have subtitles separate from title
        return null;
    }

    private Set<String> getAuthors(Document doc) {
        Set<String> authors = new HashSet<>();
        try {
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                // Collect authors from "作者" span
                Element authorSpan = infoElement.selectFirst("span:contains(作者)");
                if (authorSpan != null) {
                    Element sibling = authorSpan.nextElementSibling();
                    while (sibling != null && "a".equals(sibling.tagName())) {
                        authors.add(sibling.text().trim());
                        sibling = sibling.nextElementSibling();
                    }
                }
                // Collect translators from "译者" span
                Element translatorSpan = infoElement.selectFirst("span:contains(译者)");
                if (translatorSpan != null) {
                    Element sibling = translatorSpan.nextElementSibling();
                    while (sibling != null && "a".equals(sibling.tagName())) {
                        authors.add(sibling.text().trim());
                        sibling = sibling.nextElementSibling();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse authors: {}", e.getMessage());
        }
        return authors.isEmpty() ? Set.of() : authors;
    }

    private String getDescription(Document doc) {
        try {
            Element descElement = doc.selectFirst("#link-report .intro");
            if (descElement != null) {
                return descElement.html();
            }
            // Fallback to content introduction
            Element contentElement = doc.selectFirst("#content .related_info .intro");
            if (contentElement != null) {
                return contentElement.html();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse description: {}", e.getMessage());
        }
        return null;
    }

    private String getIsbn10(Document doc) {
        try {
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                Element span = infoElement.selectFirst("span:contains(ISBN)");
                if (span != null) {
                    Node next = span.nextSibling();
                    if (next instanceof TextNode) {
                        String isbn = ((TextNode) next).text().trim();
                        String digits = isbn.replaceAll("[^\\d]", "");
                        if (digits.length() == 10) {
                            return digits;
                        }
                    }
                }
            }
            log.debug("Failed to parse ISBN10: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse ISBN10: {}", e.getMessage());
        }
        return null;
    }

    private String getIsbn13(Document doc) {
        try {
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                Element span = infoElement.selectFirst("span:contains(ISBN)");
                if (span != null) {
                    Node next = span.nextSibling();
                    if (next instanceof TextNode) {
                        String isbn = ((TextNode) next).text().trim();
                        String digits = isbn.replaceAll("[^\\d]", "");
                        if (digits.length() == 13) {
                            return digits;
                        }
                    }
                }
            }
            log.debug("Failed to parse ISBN13: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse ISBN13: {}", e.getMessage());
        }
        return null;
    }

    private String getPublisher(Document doc) {
        try {
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                Elements spans = infoElement.select("span");
                for (Element span : spans) {
                    if (span.text().contains("出版社")) {
                        Element publisherElement = span.nextElementSibling();
                        if (publisherElement != null) {
                            return publisherElement.text().trim();
                        }
                    }
                }
            }
            log.warn("Failed to parse publisher: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse publisher: {}", e.getMessage());
        }
        return null;
    }

    private LocalDate getPublicationDate(Document doc) {
        try {
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                Element span = infoElement.selectFirst("span:contains(出版年)");
                if (span != null) {
                    Node next = span.nextSibling();
                    if (next instanceof TextNode) {
                        String dateText = ((TextNode) next).text().trim();
                        return parseDoubanDate(dateText);
                    }
                }
            }
            log.warn("Failed to parse publishedDate: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse publishedDate: {}", e.getMessage());
        }
        return null;
    }

    private String getSeriesName(Document doc) {
        try {
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                Element seriesSpan = infoElement.selectFirst("span:contains(丛书)");
                if (seriesSpan != null) {
                    Element seriesLink = seriesSpan.nextElementSibling();
                    if (seriesLink != null && "a".equals(seriesLink.tagName())) {
                        String seriesName = seriesLink.text().trim();
                        if (!seriesName.isEmpty()) {
                            log.debug("Found series name: {}", seriesName);
                            return seriesName;
                        }
                    }
                }
            }
            log.debug("No series information found");
        } catch (Exception e) {
            log.warn("Failed to parse series name: {}", e.getMessage());
        }
        return null;
    }

    private Float getSeriesNumber(Document doc) {
        // Douban doesn't typically have series information
        return null;
    }

    private Integer getSeriesTotal(Document doc) {
        // Douban doesn't typically have series information
        return null;
    }

    private String getLanguage(Document doc) {
        // Douban doesn't typically specify language
        return null;
    }

    private Set<String> getCategories(Document doc) {
        // Douban doesn't typically have category information
        return Set.of();
    }

    private Double getRating(Document doc) {
        try {
            Element ratingElement = doc.selectFirst("#interest_sectl .rating_num");
            if (ratingElement != null) {
                String ratingText = ratingElement.text().trim();
                if (!ratingText.isEmpty()) {
                    return Double.parseDouble(ratingText);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Douban rating: {}", e.getMessage());
        }
        return null;
    }

    private List<BookReview> getReviews(Document doc, int maxReviews) {
        List<BookReview> reviews = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);

        try {
            Elements reviewElements = doc.select("#comments .comment-item");
            log.debug("Douban: Found {} review elements with selector '#comments .comment-item'", reviewElements.size());

            if (reviewElements.isEmpty()) {
                // Try alternative selectors that Douban might be using
                reviewElements = doc.select(".comment-item");
                log.debug("Douban: Trying alternative selector '.comment-item', found {} elements", reviewElements.size());

                if (reviewElements.isEmpty()) {
                    reviewElements = doc.select("[class*=comment]");
                    log.debug("Douban: Trying broad selector '[class*=comment]', found {} elements", reviewElements.size());
                }
            }

            int count = 0;

            for (Element reviewElement : reviewElements) {
                if (count >= maxReviews) {
                    break;
                }

                Elements reviewerElements = reviewElement.select(".comment-info a");
                String reviewerName = !reviewerElements.isEmpty() ? reviewerElements.first().text() : null;

                Elements ratingElements = reviewElement.select(".comment-info .rating");
                Float ratingValue = null;
                if (!ratingElements.isEmpty()) {
                    String ratingClass = ratingElements.first().className();
                    Pattern pattern = Pattern.compile("rating(\\d+)");
                    Matcher matcher = pattern.matcher(ratingClass);
                    if (matcher.find()) {
                        ratingValue = Float.parseFloat(matcher.group(1)) / 2.0f; // Convert 5-star scale to 10-star scale
                    }
                }

                Elements dateElements = reviewElement.select(".comment-info .comment-time");
                Instant dateInstant = null;
                if (!dateElements.isEmpty()) {
                    try {
                        String dateText = dateElements.first().text().trim();
                        LocalDate localDate = LocalDate.parse(dateText, formatter);
                        dateInstant = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                    } catch (DateTimeParseException e) {
                        log.warn("Failed to parse date '{}' in review: {}", dateElements.first().text(), e.getMessage());
                    }
                }

                Elements bodyElements = reviewElement.select(".comment-content");
                String body = !bodyElements.isEmpty() ? bodyElements.first().text() : null;
                if (body == null || body.isEmpty()) {
                    continue;
                }

                reviews.add(BookReview.builder()
                        .metadataProvider(MetadataProvider.Douban)
                        .reviewerName(reviewerName != null ? reviewerName.trim() : null)
                        .rating(ratingValue)
                        .date(dateInstant)
                        .body(body.trim())
                        .build());

                count++;
            }
        } catch (Exception e) {
            log.warn("Failed to parse reviews: {}", e.getMessage());
        }
        log.info("Douban: Extracted {} reviews from page", reviews.size());
        return reviews;
    }

    private Integer getReviewCount(Document doc) {
        try {
            Element reviewCountElement = doc.selectFirst("#interest_sectl .rating_people span");
            if (reviewCountElement != null) {
                String reviewCountRaw = reviewCountElement.text().replaceAll("[^\\d]", "");
                if (!reviewCountRaw.isEmpty()) {
                    return Integer.parseInt(reviewCountRaw);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Douban review count: {}", e.getMessage());
        }
        return null;
    }

    private String getThumbnail(Document doc) {
        try {
            Element imageElement = doc.selectFirst("#mainpic a");
            log.debug(imageElement.toString());
            if (imageElement != null) {
                return imageElement.attr("href");
            }
            log.warn("Failed to parse thumbnail: No suitable image URL found.");
        } catch (Exception e) {
            log.warn("Failed to parse thumbnail: {}", e.getMessage());
        }
        return null;
    }

    private Integer getPageCount(Document doc) {
        try {
            Element infoElement = doc.selectFirst("#info");
            if (infoElement != null) {
                Element span = infoElement.selectFirst("span:contains(页数)");
                if (span != null) {
                    Node next = span.nextSibling();
                    if (next instanceof TextNode) {
                        String pageText = ((TextNode) next).text().trim();
                        if (!pageText.isEmpty()) {
                            try {
                                return Integer.parseInt(pageText.replaceAll("[^\\d]", ""));
                            } catch (NumberFormatException e) {
                                log.warn("Error parsing page count: {}", pageText);
                            }
                        }
                    }
                }
            }
            log.debug("Failed to parse page count: Element not found.");
        } catch (Exception e) {
            log.warn("Failed to parse page count: {}", e.getMessage());
        }
        return null;
    }

    private Document fetchDocument(String url) {
        try {
            Connection connection = Jsoup.connect(url)
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("accept-language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7,fr-CH;q=0.6,fr;q=0.5")
                    .header("accept-encoding", "identity")
                    .header("cache-control", "no-cache")
                    .header("origin", "https://www.douban.com")
                    .header("sec-ch-ua", "\"Not;A=Brand\";v=\"99\", \"Microsoft Edge\";v=\"139\", \"Chromium\";v=\"139\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .header("sec-fetch-dest", "empty")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-site", "same-origin")
                    .header("sec-fetch-user", "?1")
                    .header("upgrade-insecure-requests", "1")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36 Edg/139.0.0.0")
                    .timeout(15000)
                    .method(Connection.Method.GET)
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .followRedirects(true);

            Connection.Response response = connection.execute();

            // Get the response content
            String html = response.body();
            return Jsoup.parse(html, response.url().toString());
        } catch (IOException e) {
            log.error("Error parsing url: {}", url, e);
            throw new RuntimeException(e);
        }
    }

    private LocalDate parseDoubanDate(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }
        dateString = dateString.trim();

        // Try regex patterns first for more flexibility
        try {
            // Pattern for yyyy-MM-dd or yyyy-M-d or yyyy-MM-d or yyyy-M-dd
            Pattern pattern = Pattern.compile("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})");
            Matcher matcher = pattern.matcher(dateString);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            }

            // Pattern for yyyy-MM or yyyy-M
            pattern = Pattern.compile("(\\d{4})[-/](\\d{1,2})");
            matcher = pattern.matcher(dateString);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                return LocalDate.of(year, month, 1);
            }

            // Pattern for yyyy
            pattern = Pattern.compile("(\\d{4})");
            matcher = pattern.matcher(dateString);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                return LocalDate.of(year, 1, 1);
            }

            // Chinese patterns
            // Pattern for 年月日: 2011年1月10日
            pattern = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
            matcher = pattern.matcher(dateString);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDate.of(year, month, day);
            }

            // Pattern for 年月: 2111年12月
            pattern = Pattern.compile("(\\d{4})年(\\d{1,2})月");
            matcher = pattern.matcher(dateString);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                return LocalDate.of(year, month, 1);
            }

            // Pattern for 年: 2011年
            pattern = Pattern.compile("(\\d{4})年");
            matcher = pattern.matcher(dateString);
            if (matcher.matches()) {
                int year = Integer.parseInt(matcher.group(1));
                return LocalDate.of(year, 1, 1);
            }

        } catch (Exception e) {
            log.warn("Exception while parsing date '{}': {}", dateString, e.getMessage());
        }

        return null;
    }

    private boolean areDoubanReviewsEnabled() {
        return appSettingService.getAppSettings()
                .getMetadataPublicReviewsSettings()
                .getProviders()
                .stream()
                .anyMatch(cfg -> cfg.getProvider() == MetadataProvider.Douban && cfg.isEnabled());
    }

    private String cleanDescriptionHtml(String html) {
        try {
            if (html == null || html.isEmpty()) {
                return html;
            }
            Document document = Jsoup.parse(html);
            return document.body().text();
        } catch (Exception e) {
            log.warn("Error cleaning html description, Error: {}", e.getMessage());
        }
        return html;
    }
}
