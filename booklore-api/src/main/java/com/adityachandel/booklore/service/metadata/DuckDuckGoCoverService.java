package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.CoverImage;
import com.adityachandel.booklore.model.dto.request.CoverFetchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class DuckDuckGoCoverService implements BookCoverProvider {

    private static final String SEARCH_BASE_URL = "https://duckduckgo.com/?q=";
    private static final String JSON_BASE_URL = "https://duckduckgo.com/i.js?o=json&q=";
    private static final String SITE_FILTER = "+(site%3Aamazon.com+OR+site%3Agoodreads.com)";
    private static final String SEARCH_PARAMS = "&iar=images&iaf=size%3ALarge%2Clayout%3ATall";
    private static final String JSON_PARAMS = "&iar=images&iaf=size%3ALarge%2Clayout%3ATall";

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final String REFERRER = "https://duckduckgo.com/";
    private static final Map<String, String> DEFAULT_HEADERS = Map.ofEntries(
            Map.entry("accept", "text/html, application/json"),
            Map.entry("content-type", "application/json"),
            Map.entry("user-agent", USER_AGENT)
    );

    private final ObjectMapper mapper;

    public List<CoverImage> getCovers(CoverFetchRequest request) {
        String title = request.getTitle();
        String author = request.getAuthor();
        String searchTerm = (author != null && !author.isEmpty())
                ? title + " " + author + " book"
                : title + " book";

        String encodedSiteQuery = URLEncoder.encode(searchTerm, StandardCharsets.UTF_8);
        String siteUrl = SEARCH_BASE_URL + encodedSiteQuery + SITE_FILTER + SEARCH_PARAMS;
        Document siteDoc = getDocument(siteUrl);
        Pattern tokenPattern = Pattern.compile("vqd=\"(\\d+-\\d+)\"");
        Matcher siteMatcher = tokenPattern.matcher(siteDoc.html());
        if (!siteMatcher.find()) {
            log.error("Could not find search token for site-filtered images!");
            return Collections.emptyList();
        }
        String siteSearchToken = siteMatcher.group(1);
        List<CoverImage> siteFilteredImages = fetchImagesFromApi(searchTerm + " (site:amazon.com OR site:goodreads.com)", siteSearchToken);
        siteFilteredImages.removeIf(dto -> dto.getWidth() < 350);
        siteFilteredImages.removeIf(dto -> dto.getWidth() >= dto.getHeight());
        if (siteFilteredImages.size() > 7) {
            siteFilteredImages = siteFilteredImages.subList(0, 7);
        }

        String generalQuery = searchTerm;
        String encodedGeneralQuery = URLEncoder.encode(generalQuery, StandardCharsets.UTF_8);
        String generalUrl = SEARCH_BASE_URL + encodedGeneralQuery + SEARCH_PARAMS;
        Document generalDoc = getDocument(generalUrl);
        Matcher generalMatcher = tokenPattern.matcher(generalDoc.html());
        List<CoverImage> generalBookImages = new ArrayList<>();
        if (generalMatcher.find()) {
            String generalSearchToken = generalMatcher.group(1);
            generalBookImages = fetchImagesFromApi(generalQuery, generalSearchToken);
            generalBookImages.removeIf(dto -> dto.getWidth() < 350);
            generalBookImages.removeIf(dto -> dto.getWidth() >= dto.getHeight());
            Set<String> siteUrls = siteFilteredImages.stream().map(CoverImage::getUrl).collect(Collectors.toSet());
            generalBookImages.removeIf(dto -> siteUrls.contains(dto.getUrl()));
            if (generalBookImages.size() > 10) {
                generalBookImages = generalBookImages.subList(0, 10);
            }
        }

        List<CoverImage> allImages = new ArrayList<>(siteFilteredImages);
        allImages.addAll(generalBookImages);

        for (int i = 0; i < allImages.size(); i++) {
            CoverImage img = allImages.get(i);
            allImages.set(i, new CoverImage(
                    img.getUrl(),
                    img.getWidth(),
                    img.getHeight(),
                    i + 1
            ));
        }

        return allImages;
    }

    private List<CoverImage> fetchImagesFromApi(String query, String searchToken) {
        List<CoverImage> priority = new ArrayList<>();
        List<CoverImage> others = new ArrayList<>();
        try {
            String url = JSON_BASE_URL
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + JSON_PARAMS
                    + "&vqd=" + searchToken;

            Connection.Response resp = Jsoup.connect(url)
                    .ignoreContentType(true)
                    .referrer(REFERRER)
                    .followRedirects(true)
                    .headers(DEFAULT_HEADERS)
                    .method(Connection.Method.GET)
                    .execute();

            String json = resp.body();
            JsonNode results = mapper.readTree(json).path("results");
            if (results.isArray()) {
                for (JsonNode img : results) {
                    String link = img.path("image").asText();
                    int w = img.path("width").asInt();
                    int h = img.path("height").asInt();
                    CoverImage dto = new CoverImage(link, w, h, 0);
                    if (link.contains("amazon") || link.contains("goodreads")) {
                        priority.add(dto);
                    } else {
                        others.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching images from DuckDuckGo", e);
        }
        List<CoverImage> all = new ArrayList<>(priority);
        all.addAll(others);
        return all;
    }

    private Document getDocument(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .referrer(REFERRER)
                    .followRedirects(true)
                    .headers(DEFAULT_HEADERS)
                    .method(Connection.Method.GET)
                    .execute();
            return response.parse();
        } catch (IOException e) {
            log.error("Error parsing url: {}", url, e);
            throw new RuntimeException(e);
        }
    }
}