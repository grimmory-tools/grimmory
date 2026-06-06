package org.booklore.model.dto.response.ranobedbapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
import java.util.Set;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RanobedbSearchResponse{
  private List<Book> books;
  private String count;
  private int currentPage;
  private int totalPages;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Book {
    private int id;
  }
}
