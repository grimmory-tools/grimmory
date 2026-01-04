package com.adityachandel.booklore.model.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class BookRecommendationLite {
    private long b; // bookId
    private double s; // similarityScore
}
