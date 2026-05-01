package org.booklore.model.dto.response.comicvineapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComicvineSingleResponse {
    private String error;
    private int limit;
    private int offset;
    @JsonProperty("number_of_page_results")
    private int numberOfPageResults;
    @JsonProperty("number_of_total_results")
    private int numberOfTotalResults;
    @JsonProperty("status_code")
    private int statusCode;
    private Comic results;
    private String version;
}
