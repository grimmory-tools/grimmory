package org.booklore.model.dto.response.perrypediaapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PerrypediaParseResponse {
    private Parse parse;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Parse {
        private String title;
        private List<Redirect> redirects;
        private String wikitext;
        private String text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Redirect {
        private String from;
        private String to;
    }
}
