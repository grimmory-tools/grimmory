package org.booklore.service.metadata.parser.hardcover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HardcoverWorkDetails {

    @JsonProperty("isbn_10")
    private String isbn10;

    @JsonProperty("isbn_13")
    private String isbn13;
}