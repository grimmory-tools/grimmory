package org.booklore.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KoreaderBookResponse {
    private Long id;
    private String title;
    private List<String> authors;
    private String isbn10;
    private String isbn13;
    private Integer pagecount;
}
