package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "book_metadata_category_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(BookMetadataCategoryKey.class)
public class BookMetadataCategoryMapping {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Id
    @Column(name = "category_id")
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", insertable = false, updatable = false)
    private BookMetadataEntity book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private CategoryEntity category;
}
