package org.booklore.service.browse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.booklore.util.RequestUtils.withContextPath;

class SubpathLinksTest {
    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/grimmory"})
    void preservesTheContextInFacetAndPaginationPaths(String prefix) {
        var request = new MockHttpServletRequest();
        request.setContextPath(prefix);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        for (String type : List.of("books", "authors")) {
            String path = "/api/v1/" + type;
            var builder = new FacetResponseBuilder(path + "/page", path + "/facets", "query=hello", List.of());
            assertThat(builder.selfLinks().getFirst().href()).isEqualTo(prefix + path + "/facets?query=hello");
            assertThat(builder.sortGroup(Set.of("title")).links()).allSatisfy(link ->
                    assertThat(link.href()).startsWith(prefix + path + "/page?query=hello&sort="));
            assertThat(builder.group("genre", "Genre", List.of(new FacetResponseBuilder.FacetCount("Fantasy", 2)))
                    .links().getFirst().href()).isEqualTo(prefix + path + "/page?query=hello&facet=genre%3AFantasy");
            assertThat(withContextPath(path + "/page")).isEqualTo(prefix + path + "/page");
        }
    }
}
