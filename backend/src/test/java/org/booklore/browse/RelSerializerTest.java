package org.booklore.browse;

import org.booklore.model.dto.browse.FacetGroupsResponse.FacetLink;
import org.booklore.model.dto.browse.FacetGroupsResponse.Properties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Uses the Jackson 3 (tools.jackson) mapper, the one Spring Boot wires into HTTP
// message conversion; a Jackson 2 mapper would ignore the rel serializer entirely.
class RelSerializerTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void emptyRelSerializesAsEmptyArray() {
        String json = mapper.writeValueAsString(Link.json(List.of(), "/api/v1/books/facets"));
        assertThat(json).isEqualTo("{\"rel\":[],\"href\":\"/api/v1/books/facets\",\"type\":\"application/json\"}");
    }

    @Test
    void singleRelSerializesAsString() {
        String json = mapper.writeValueAsString(Link.json(List.of("self"), "/api/v1/books/facets"));
        assertThat(json).isEqualTo("{\"rel\":\"self\",\"href\":\"/api/v1/books/facets\",\"type\":\"application/json\"}");
    }

    @Test
    void multipleRelsSerializeAsArray() {
        String json = mapper.writeValueAsString(Link.json(List.of("first", "previous"), "/api/v1/books/page"));
        assertThat(json).isEqualTo("{\"rel\":[\"first\",\"previous\"],\"href\":\"/api/v1/books/page\",\"type\":\"application/json\"}");
    }

    @Test
    void facetLinkFollowsTheSameConvention() {
        FacetLink inactive = new FacetLink(List.of("facet"), "/x", Link.JSON_TYPE, "Horror", "Horror", new Properties(2L));
        assertThat(mapper.writeValueAsString(inactive))
                .isEqualTo("{\"rel\":\"facet\",\"href\":\"/x\",\"type\":\"application/json\",\"title\":\"Horror\",\"value\":\"Horror\",\"properties\":{\"numberOfItems\":2}}");

        FacetLink active = new FacetLink(List.of("self", "facet"), "/x", Link.JSON_TYPE, "Horror", "Horror", new Properties(2L));
        assertThat(mapper.writeValueAsString(active))
                .isEqualTo("{\"rel\":[\"self\",\"facet\"],\"href\":\"/x\",\"type\":\"application/json\",\"title\":\"Horror\",\"value\":\"Horror\",\"properties\":{\"numberOfItems\":2}}");
    }
}
