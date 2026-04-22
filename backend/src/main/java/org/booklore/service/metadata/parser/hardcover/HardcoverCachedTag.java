package org.booklore.service.metadata.parser.hardcover;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public class HardcoverCachedTag {
      private String tag;
      
      @JsonProperty("tagSlug")
      private String tagSlug;
      
      private String category;
      
      @JsonProperty("categorySlug")
      private String categorySlug;
      
      @JsonProperty("spoilerRatio")
      private Double spoilerRatio;
      
      private Integer count;
  }

