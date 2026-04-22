package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "book_metadata_author_mapping")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(BookMetadataAuthorKey.class)
public class BookMetadataAuthorMapping {

    @Id
    @Column(name = "book_id")
    private Long bookId;

    @Id
    @Column(name = "author_id")
    private Long authorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", insertable = false, updatable = false)
    private BookMetadataEntity book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private AuthorEntity author;
}
