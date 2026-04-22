package org.booklore.model.dto.response.comicvineapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComicvineApiResponse {
    private String error;
    private int limit;
    private int offset;
    
    @JsonProperty("number_of_page_results")
    private int numberOfPageResults;
    
    @JsonProperty("number_of_total_results")
    private int numberOfTotalResults;
    
    @JsonProperty("status_code")
    private int statusCode;
    private List<Comic> results;
    private String version;
}