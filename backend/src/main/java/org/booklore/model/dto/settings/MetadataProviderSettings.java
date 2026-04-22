package org.booklore.model.dto.settings;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MetadataProviderSettings {
    private Amazon amazon;
    private Google google;
    private Goodreads goodReads;
    private Hardcover hardcover;
    private Comicvine comicvine;
    private Ranobedb ranobedb;
    private Douban douban;
    @JsonProperty("lubimyczytac")
    private Lubimyczytac lubimyczytac;
    private Audible audible;

    @Data
    public static class Amazon {
        private boolean enabled;
        private String cookie;
        private String domain;
    }

    @Data
    public static class Google {
        private boolean enabled;
        private String language;
        private String apiKey;
    }

    @Data
    public static class Goodreads {
        private boolean enabled;
    }

    @Data
    public static class Hardcover {
        private boolean enabled;
        private String apiKey;
    }

    @Data
    public static class Comicvine {
        private boolean enabled;
        private String apiKey;
    }

    @Data
    public static class Ranobedb {
        private boolean enabled;
    }

    @Data
    public static class Douban {
        private boolean enabled;
    }

    @Data
    public static class Lubimyczytac {
        private boolean enabled;
    }

    @Data
    public static class Audible {
        private boolean enabled;
        private String domain;
    }
}
