package org.booklore.model.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BookRecommendation {
    private Book book;
    private double similarityScore;
}
