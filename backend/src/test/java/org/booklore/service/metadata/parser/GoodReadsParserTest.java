package org.booklore.service.metadata.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class GoodReadsParserTest {

    @Test
    void isAwsWafChallenge_detectsWafChallengeHtml() throws IOException {
        Document doc = Jsoup.parse(readFixture("waf-challenge.html"));

        assertThat(GoodReadsParser.isAwsWafChallenge(doc))
                .as("AWS WAF challenge page must be detected as a failure")
                .isTrue();
    }

    @Test
    void isAwsWafChallenge_doesNotFlagRegularSearchPage() {
        Document doc = Jsoup.parse(
                "<html><body><table class='tableList'>"
                        + "<tr itemtype='http://schema.org/Book'></tr>"
                        + "</table></body></html>");

        assertThat(GoodReadsParser.isAwsWafChallenge(doc)).isFalse();
    }

    @Test
    void isAwsWafChallenge_handlesNullDocument() {
        assertThat(GoodReadsParser.isAwsWafChallenge(null)).isFalse();
    }

    private String readFixture(String fixtureName) throws IOException {
        String filename = Paths.get("goodreads", fixtureName + ".fixture").toString();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assertThat(is).as("Fixture %s should be on the classpath", filename).isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
