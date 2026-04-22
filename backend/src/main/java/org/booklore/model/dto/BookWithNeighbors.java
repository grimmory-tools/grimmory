package org.booklore.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookWithNeighbors {
    private Book currentBook;
    private Long previousBookId;
    private Long nextBookId;
}
