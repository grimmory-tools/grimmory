package org.booklore.service.kobo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class KepubHtmlConversionServiceTest {
    @InjectMocks
    private KepubHtmlConversionService service;

    @Test
    void transform_ShouldSplitSentences() {
        String actual = service.transform("<html><body><p>Hello.World.  This is a test!</p></body></html>", false);


        assertThat(actual).contains(
                "<span id=\"kobo.1\" class=\"koboSpan\">Hello.</span>"
        );
        assertThat(actual).contains(
                "<span id=\"kobo.2\" class=\"koboSpan\">World.</span>"
        );
        assertThat(actual).contains(
                "<span id=\"kobo.3\" class=\"koboSpan\"> This is a test!</span>"
        );
    }

    @Test
    void transform_ShouldIncludeCSSHacks() {
        String actual = service.transform("<html><body><p>Hello World.</p></body></html>", false);

        assertThat(actual).contains("class=\"kobostylehacks\"");
    }

    @Test
    void transform_ShouldIncludeRootXmlns() {
        String actual = service.transform("<html><body><p>Hello World.</p></body></html>", false);

        assertThat(actual).contains("xmlns=\"http://www.w3.org/1999/xhtml\"");
    }
}