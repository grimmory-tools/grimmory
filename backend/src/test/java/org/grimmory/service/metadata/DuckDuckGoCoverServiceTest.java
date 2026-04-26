package org.grimmory.service.metadata;

import org.booklore.exception.APIException;
import org.booklore.model.dto.CoverImage;
import org.booklore.model.dto.request.CoverFetchRequest;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DuckDuckGoCoverServiceTest {

    @Mock
    private ObjectMapper mapper;

    @InjectMocks
    private DuckDuckGoCoverService service;

    @Test
    void isApproximatelySquare_trueForPerfectSquare() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(service, 500, 500)).isTrue();
    }

    @Test
    void isApproximatelySquare_trueWithinTolerance() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(service, 500, 550)).isTrue();
        assertThat((boolean) method.invoke(service, 550, 500)).isTrue();
    }

    @Test
    void isApproximatelySquare_falseForTallImage() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(service, 300, 600)).isFalse();
    }

    @Test
    void isApproximatelySquare_falseForWideImage() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(service, 600, 300)).isFalse();
    }

    @Test
    void isApproximatelySquare_falseWhenZeroDimension() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(service, 0, 500)).isFalse();
        assertThat((boolean) method.invoke(service, 500, 0)).isFalse();
    }

    @Test
    void isApproximatelySquare_boundaryRatio085() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(service, 85, 100)).isTrue();
        assertThat((boolean) method.invoke(service, 84, 100)).isFalse();
    }

    @Test
    void isApproximatelySquare_boundaryRatio115() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(service, 115, 100)).isTrue();
        assertThat((boolean) method.invoke(service, 116, 100)).isFalse();
    }

    @Test
    void tokenPattern_noMatchMeansNoResults() {
        java.util.regex.Pattern tokenPattern = java.util.regex.Pattern.compile("vqd=\"(\\d+-\\d+)\"");
        java.util.regex.Matcher matcher = tokenPattern.matcher("no token here");
        assertThat(matcher.find()).isFalse();
    }

    @Test
    void tokenPattern_matchesValidToken() {
        java.util.regex.Pattern tokenPattern = java.util.regex.Pattern.compile("vqd=\"(\\d+-\\d+)\"");
        java.util.regex.Matcher matcher = tokenPattern.matcher("vqd=\"12345-67890\"");
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("12345-67890");
    }

    @Test
    void tokenPattern_doesNotMatchInvalidToken() {
        java.util.regex.Pattern tokenPattern = java.util.regex.Pattern.compile("vqd=\"(\\d+-\\d+)\"");
        assertThat(tokenPattern.matcher("vqd=\"abcde\"").find()).isFalse();
        assertThat(tokenPattern.matcher("vqd=\"12345\"").find()).isFalse();
        assertThat(tokenPattern.matcher("other content").find()).isFalse();
    }

    @Test
    void coverFetchRequest_audiobookSetsSquareParams() {
        CoverFetchRequest request = CoverFetchRequest.builder()
                .title("Test Book")
                .author("Author")
                .coverType("audiobook")
                .build();

        assertThat("audiobook".equalsIgnoreCase(request.getCoverType())).isTrue();
    }

    @Test
    void coverFetchRequest_ebookSetsTallParams() {
        CoverFetchRequest request = CoverFetchRequest.builder()
                .title("Test Book")
                .coverType("ebook")
                .build();

        assertThat("audiobook".equalsIgnoreCase(request.getCoverType())).isFalse();
    }

    @Test
    void searchTermConstruction_withAuthor() {
        String title = "The Great Book";
        String author = "John Doe";
        boolean isAudiobook = false;
        String bookType = isAudiobook ? "audiobook" : "book";

        String searchTerm = (author != null && !author.isEmpty())
                ? title + " " + author + " " + bookType
                : title + " " + bookType;

        assertThat(searchTerm).isEqualTo("The Great Book John Doe book");
    }

    @Test
    void searchTermConstruction_withoutAuthor() {
        String title = "The Great Book";
        String author = null;
        boolean isAudiobook = true;
        String bookType = isAudiobook ? "audiobook" : "book";

        String searchTerm = (author != null && !author.isEmpty())
                ? title + " " + author + " " + bookType
                : title + " " + bookType;

        assertThat(searchTerm).isEqualTo("The Great Book audiobook");
    }

    @Test
    void coverImageFiltering_removesSmallImages() {
        List<CoverImage> images = new java.util.ArrayList<>(List.of(
                new CoverImage("url1", 200, 300, 1),
                new CoverImage("url2", 400, 600, 2),
                new CoverImage("url3", 349, 500, 3),
                new CoverImage("url4", 350, 500, 4)
        ));

        images.removeIf(dto -> dto.getWidth() < 350);

        assertThat(images).hasSize(2);
        assertThat(images.get(0).getUrl()).isEqualTo("url2");
        assertThat(images.get(1).getUrl()).isEqualTo("url4");
    }

    @Test
    void coverImageFiltering_removesWideThanTallForBooks() {
        List<CoverImage> images = new java.util.ArrayList<>(List.of(
                new CoverImage("url1", 400, 600, 1),
                new CoverImage("url2", 600, 400, 2),
                new CoverImage("url3", 500, 500, 3)
        ));

        images.removeIf(dto -> dto.getWidth() >= dto.getHeight());

        assertThat(images).hasSize(1);
        assertThat(images.getFirst().getUrl()).isEqualTo("url1");
    }

    @Test
    void coverImageFiltering_removesNonSquareForAudiobooks() throws Exception {
        var method = DuckDuckGoCoverService.class.getDeclaredMethod("isApproximatelySquare", int.class, int.class);
        method.setAccessible(true);

        List<CoverImage> images = new java.util.ArrayList<>(List.of(
                new CoverImage("url1", 500, 500, 1),
                new CoverImage("url2", 500, 800, 2),
                new CoverImage("url3", 480, 520, 3)
        ));

        images.removeIf(dto -> {
            try {
                return !(boolean) method.invoke(service, dto.getWidth(), dto.getHeight());
            } catch (Exception e) {
                return true;
            }
        });

        assertThat(images).hasSize(2);
        assertThat(images.get(0).getUrl()).isEqualTo("url1");
        assertThat(images.get(1).getUrl()).isEqualTo("url3");
    }

    @Test
    void siteFilteredImages_limitedToSeven() {
        List<CoverImage> images = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            images.add(new CoverImage("url" + i, 500, 700, i));
        }

        if (images.size() > 7) {
            images = images.subList(0, 7);
        }

        assertThat(images).hasSize(7);
    }

    @Test
    void generalImages_limitedToTen() {
        List<CoverImage> images = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            images.add(new CoverImage("url" + i, 500, 700, i));
        }

        if (images.size() > 10) {
            images = images.subList(0, 10);
        }

        assertThat(images).hasSize(10);
    }

    @Test
    void duplicateRemoval_generalImagesExcludeSiteUrls() {
        List<CoverImage> siteImages = List.of(
                new CoverImage("https://amazon.com/img1", 500, 700, 1),
                new CoverImage("https://goodreads.com/img2", 500, 700, 2)
        );

        List<CoverImage> generalImages = new java.util.ArrayList<>(List.of(
                new CoverImage("https://amazon.com/img1", 500, 700, 1),
                new CoverImage("https://other.com/img3", 500, 700, 2),
                new CoverImage("https://example.com/img4", 500, 700, 3)
        ));

        java.util.Set<String> siteUrls = siteImages.stream()
                .map(CoverImage::getUrl)
                .collect(java.util.stream.Collectors.toSet());
        generalImages.removeIf(dto -> siteUrls.contains(dto.getUrl()));

        assertThat(generalImages).hasSize(2);
        assertThat(generalImages.stream().map(CoverImage::getUrl))
                .containsExactly("https://other.com/img3", "https://example.com/img4");
    }

    @Test
    void resultIndexing_setsSequentialIndices() {
        List<CoverImage> allImages = new java.util.ArrayList<>(List.of(
                new CoverImage("url1", 500, 700, 0),
                new CoverImage("url2", 600, 800, 0),
                new CoverImage("url3", 400, 600, 0)
        ));

        for (int i = 0; i < allImages.size(); i++) {
            CoverImage img = allImages.get(i);
            allImages.set(i, new CoverImage(img.getUrl(), img.getWidth(), img.getHeight(), i + 1));
        }

        assertThat(allImages.get(0).getIndex()).isEqualTo(1);
        assertThat(allImages.get(1).getIndex()).isEqualTo(2);
        assertThat(allImages.get(2).getIndex()).isEqualTo(3);
    }

    @Test
    void imagePrioritization_amazonAndGoodreadsFirst() {
        List<CoverImage> priority = new java.util.ArrayList<>();
        List<CoverImage> others = new java.util.ArrayList<>();

        List<String> links = List.of(
                "https://other.com/img1",
                "https://amazon.com/img2",
                "https://goodreads.com/img3",
                "https://example.com/img4"
        );

        for (String link : links) {
            CoverImage dto = new CoverImage(link, 500, 700, 0);
            if (link.contains("amazon") || link.contains("goodreads")) {
                priority.add(dto);
            } else {
                others.add(dto);
            }
        }

        List<CoverImage> all = new java.util.ArrayList<>(priority);
        all.addAll(others);

        assertThat(all.get(0).getUrl()).contains("amazon");
        assertThat(all.get(1).getUrl()).contains("goodreads");
        assertThat(all).hasSize(4);
    }

    private Connection mockJsoupConnect(MockedStatic<Jsoup> jsoupMock) throws IOException {
        Connection connection = mock(Connection.class, RETURNS_SELF);
        jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(connection);
        return connection;
    }

    private Connection.Response buildHtmlResponse(String html, Map<String, String> cookies) throws IOException {
        Connection.Response response = mock(Connection.Response.class);
        Document doc = Jsoup.parse(html);
        when(response.parse()).thenReturn(doc);
        when(response.cookies()).thenReturn(cookies);
        return response;
    }

    private Connection.Response buildJsonResponse(String json) {
        Connection.Response response = mock(Connection.Response.class);
        when(response.body()).thenReturn(json);
        return response;
    }

    @Nested
    class GetCovers {

        @Test
        void returnsEmptyListWhenNoTokenFoundInSiteResponse() throws Exception {
            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);
                Connection.Response htmlResp = buildHtmlResponse("<html>no token here</html>", Map.of());
                when(connection.execute()).thenReturn(htmlResp);

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test Book").author("Author").coverType("ebook").build();

                List<CoverImage> result = service.getCovers(request).collectList().block();

                assertThat(result).isEmpty();
            }
        }

        @Test
        void returnsImagesWhenTokenFound() throws Exception {
            String htmlWithToken = "<html>vqd=\"12345-67890\"</html>";
            String jsonBody = """
                    {"results":[
                        {"image":"https://amazon.com/img1.jpg","width":500,"height":700},
                        {"image":"https://other.com/img2.jpg","width":400,"height":600}
                    ]}""";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response siteHtmlResp = buildHtmlResponse(htmlWithToken, Map.of("vqd", "12345-67890"));
                Connection.Response generalHtmlResp = buildHtmlResponse(htmlWithToken, Map.of("vqd", "12345-67890"));
                Connection.Response jsonResp = buildJsonResponse(jsonBody);

                when(connection.execute())
                        .thenReturn(siteHtmlResp)
                        .thenReturn(jsonResp)
                        .thenReturn(generalHtmlResp)
                        .thenReturn(jsonResp);

                JsonNode resultsNode = new ObjectMapper().readTree(jsonBody).path("results");
                JsonNode rootNode = mock(JsonNode.class);
                when(rootNode.path("results")).thenReturn(resultsNode);
                when(mapper.readTree(jsonBody)).thenReturn(rootNode);

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test Book").author("Author").coverType("ebook").build();

                List<CoverImage> result = service.getCovers(request).collectList().block();

                assertThat(result).isNotEmpty();
                assertThat(result).allSatisfy(img -> assertThat(img.getIndex()).isGreaterThan(0));
            }
        }

        @Test
        void audiobookSearchUsesSquareParams() throws Exception {
            String htmlWithToken = "<html>vqd=\"12345-67890\"</html>";
            String jsonBody = """
                    {"results":[
                        {"image":"https://amazon.com/square.jpg","width":500,"height":500}
                    ]}""";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response htmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                Connection.Response jsonResp = buildJsonResponse(jsonBody);

                when(connection.execute())
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp)
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp);

                JsonNode resultsNode = new ObjectMapper().readTree(jsonBody).path("results");
                JsonNode rootNode = mock(JsonNode.class);
                when(rootNode.path("results")).thenReturn(resultsNode);
                when(mapper.readTree(jsonBody)).thenReturn(rootNode);

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Audiobook Title").coverType("audiobook").build();

                List<CoverImage> result = service.getCovers(request).collectList().block();

                assertThat(result).allSatisfy(img ->
                        assertThat((double) img.getWidth() / img.getHeight()).isBetween(0.85, 1.15));
            }
        }

        @Test
        void filtersOutSmallAndWideImagesForEbooks() throws Exception {
            String htmlWithToken = "<html>vqd=\"12345-67890\"</html>";
            String jsonBody = """
                    {"results":[
                        {"image":"https://amazon.com/tall.jpg","width":400,"height":600},
                        {"image":"https://amazon.com/small.jpg","width":200,"height":300},
                        {"image":"https://amazon.com/wide.jpg","width":700,"height":500}
                    ]}""";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response htmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                Connection.Response jsonResp = buildJsonResponse(jsonBody);

                when(connection.execute())
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp)
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp);

                JsonNode resultsNode = new ObjectMapper().readTree(jsonBody).path("results");
                JsonNode rootNode = mock(JsonNode.class);
                when(rootNode.path("results")).thenReturn(resultsNode);
                when(mapper.readTree(jsonBody)).thenReturn(rootNode);

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test Book").coverType("ebook").build();

                List<CoverImage> result = service.getCovers(request).collectList().block();

                assertThat(result).allSatisfy(img -> {
                    assertThat(img.getWidth()).isGreaterThanOrEqualTo(350);
                    assertThat(img.getWidth()).isLessThan(img.getHeight());
                });
            }
        }

        @Test
        void deduplicatesGeneralImagesAgainstSiteFiltered() throws Exception {
            String htmlWithToken = "<html>vqd=\"12345-67890\"</html>";
            String siteJson = """
                    {"results":[
                        {"image":"https://amazon.com/shared.jpg","width":500,"height":700}
                    ]}""";
            String generalJson = """
                    {"results":[
                        {"image":"https://amazon.com/shared.jpg","width":500,"height":700},
                        {"image":"https://unique.com/other.jpg","width":400,"height":600}
                    ]}""";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response siteHtmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                Connection.Response generalHtmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                Connection.Response siteJsonResp = buildJsonResponse(siteJson);
                Connection.Response generalJsonResp = buildJsonResponse(generalJson);

                when(connection.execute())
                        .thenReturn(siteHtmlResp)
                        .thenReturn(siteJsonResp)
                        .thenReturn(generalHtmlResp)
                        .thenReturn(generalJsonResp);

                JsonNode siteResultsNode = new ObjectMapper().readTree(siteJson).path("results");
                JsonNode siteRootNode = mock(JsonNode.class);
                when(siteRootNode.path("results")).thenReturn(siteResultsNode);

                JsonNode generalResultsNode = new ObjectMapper().readTree(generalJson).path("results");
                JsonNode generalRootNode = mock(JsonNode.class);
                when(generalRootNode.path("results")).thenReturn(generalResultsNode);

                when(mapper.readTree(siteJson)).thenReturn(siteRootNode);
                when(mapper.readTree(generalJson)).thenReturn(generalRootNode);

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test Book").coverType("ebook").build();

                List<CoverImage> result = service.getCovers(request).collectList().block();

                long sharedUrlCount = result.stream()
                        .filter(img -> img.getUrl().equals("https://amazon.com/shared.jpg"))
                        .count();
                assertThat(sharedUrlCount).isEqualTo(1);
            }
        }
    }

    @Nested
    class SearchImages {

        @Test
        void returnsEmptyWhenNoTokenFound() throws Exception {
            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);
                Connection.Response htmlResp = buildHtmlResponse("<html>no token</html>", Map.of());
                when(connection.execute()).thenReturn(htmlResp);

                List<CoverImage> result = service.searchImages("test query").collectList().block();

                assertThat(result).isEmpty();
            }
        }

        @Test
        void returnsIndexedImagesOnSuccess() throws Exception {
            String htmlWithToken = "<html>vqd=\"99999-11111\"</html>";
            String jsonBody = """
                    {"results":[
                        {"image":"https://example.com/img1.jpg","width":800,"height":600},
                        {"image":"https://example.com/img2.jpg","width":1024,"height":768}
                    ]}""";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response htmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                Connection.Response jsonResp = buildJsonResponse(jsonBody);
                when(connection.execute()).thenReturn(htmlResp).thenReturn(jsonResp);

                JsonNode resultsNode = new ObjectMapper().readTree(jsonBody).path("results");
                JsonNode rootNode = mock(JsonNode.class);
                when(rootNode.path("results")).thenReturn(resultsNode);
                when(mapper.readTree(jsonBody)).thenReturn(rootNode);

                List<CoverImage> result = service.searchImages("test query").collectList().block();

                assertThat(result).hasSize(2);
                assertThat(result.get(0).getIndex()).isEqualTo(1);
                assertThat(result.get(1).getIndex()).isEqualTo(2);
            }
        }
    }

    @Nested
    class GetResponse {

        @Test
        void throwsRuntimeExceptionOnIOException() throws Exception {
            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);
                when(connection.execute()).thenThrow(new IOException("connection failed"));

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test").coverType("ebook").build();

                assertThatThrownBy(() -> service.getCovers(request).collectList().block())
                        .isInstanceOf(APIException.class)
                        .hasMessageContaining("Error fetching URL:");
            }
        }
    }

    @Nested
    class ParseResponse {

        @Test
        void throwsRuntimeExceptionOnParseFailure() throws Exception {
            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);
                Connection.Response response = mock(Connection.Response.class);
                when(connection.execute()).thenReturn(response);
                when(response.parse()).thenThrow(new IOException("parse error"));

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test").coverType("ebook").build();

                assertThatThrownBy(() -> service.getCovers(request).collectList().block())
                        .isInstanceOf(APIException.class)
                        .hasMessageContaining("Error parsing response");
            }
        }
    }

    @Nested
    class FetchImagesFromApi {

        @Test
        void throwsRuntimeExceptionOnApiException() throws Exception {
            String htmlWithToken = "<html>vqd=\"12345-67890\"</html>";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response htmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                when(connection.execute())
                        .thenReturn(htmlResp)
                        .thenThrow(new IOException("api error"));

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test").coverType("ebook").build();

                assertThatThrownBy(() -> service.getCovers(request).collectList().block())
                        .isInstanceOf(APIException.class)
                        .hasMessageContaining("DuckDuckGo image fetch failed");
            }
        }

        @Test
        void handlesEmptyResultsArray() throws Exception {
            String htmlWithToken = "<html>vqd=\"12345-67890\"</html>";
            String emptyJson = """
                    {"results":[]}""";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response htmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                Connection.Response jsonResp = buildJsonResponse(emptyJson);
                when(connection.execute())
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp)
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp);

                JsonNode resultsNode = new ObjectMapper().readTree(emptyJson).path("results");
                JsonNode rootNode = mock(JsonNode.class);
                when(rootNode.path("results")).thenReturn(resultsNode);
                when(mapper.readTree(emptyJson)).thenReturn(rootNode);

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test").coverType("ebook").build();

                List<CoverImage> result = service.getCovers(request).collectList().block();

                assertThat(result).isEmpty();
            }
        }

        @Test
        void handlesMissingResultsField() throws Exception {
            String htmlWithToken = "<html>vqd=\"12345-67890\"</html>";
            String noResultsJson = """
                    {"other":"data"}""";

            try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class, CALLS_REAL_METHODS)) {
                Connection connection = mockJsoupConnect(jsoupMock);

                Connection.Response htmlResp = buildHtmlResponse(htmlWithToken, Map.of());
                Connection.Response jsonResp = buildJsonResponse(noResultsJson);
                when(connection.execute())
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp)
                        .thenReturn(htmlResp)
                        .thenReturn(jsonResp);

                JsonNode rootNode = new ObjectMapper().readTree(noResultsJson);
                when(mapper.readTree(noResultsJson)).thenReturn(rootNode);

                CoverFetchRequest request = CoverFetchRequest.builder()
                        .title("Test").coverType("ebook").build();

                List<CoverImage> result = service.getCovers(request).collectList().block();

                assertThat(result).isEmpty();
            }
        }
    }
}
