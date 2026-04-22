package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "book_shelf_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(BookShelfKey.class)
public class BookShelfMapping {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Id
    @Column(name = "shelf_id")
    private Long shelfId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", insertable = false, updatable = false)
    private BookEntity book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shelf_id", insertable = false, updatable = false)
    private ShelfEntity shelf;
}