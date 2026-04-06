package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Entity
@Immutable
@Subselect("""
    SELECT a.id * 3 AS id,
           a.id AS original_id,
           'HIGHLIGHT' AS entry_type,
           a.user_id,
           a.book_id,
           bm.title AS book_title,
           a.text AS text_content,
           a.note AS note_content,
           a.color,
           a.style,
           a.chapter_title,
           (SELECT bf.book_type FROM book_file bf WHERE bf.book_id = a.book_id ORDER BY bf.id LIMIT 1) AS primary_book_type,
           bm.cover_updated_on,
           a.created_at,
           a.updated_at
    FROM annotations a
    JOIN book_metadata bm ON bm.book_id = a.book_id
    UNION ALL
    SELECT n.id * 3 + 1,
           n.id,
           'NOTE',
           n.user_id,
           n.book_id,
           bm.title,
           n.selected_text,
           n.note_content,
           n.color,
           NULL,
           n.chapter_title,
           (SELECT bf.book_type FROM book_file bf WHERE bf.book_id = n.book_id ORDER BY bf.id LIMIT 1),
           bm.cover_updated_on,
           n.created_at,
           n.updated_at
    FROM book_notes_v2 n
    JOIN book_metadata bm ON bm.book_id = n.book_id
    UNION ALL
    SELECT b.id * 3 + 2,
           b.id,
           'BOOKMARK',
           b.user_id,
           b.book_id,
           bm.title,
           b.title,
           b.notes,
           b.color,
           NULL,
           NULL,
           (SELECT bf.book_type FROM book_file bf WHERE bf.book_id = b.book_id ORDER BY bf.id LIMIT 1),
           bm.cover_updated_on,
           b.created_at,
           b.updated_at
    FROM book_marks b
    JOIN book_metadata bm ON bm.book_id = b.book_id
    """)
@Synchronize({"annotations", "book_notes_v2", "book_marks", "book_metadata", "book_file"})
public class NotebookEntryView {

    @Id
    private Long id;

    @Column(name = "original_id")
    private Long originalId;

    @Column(name = "entry_type")
    private String entryType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "book_id")
    private Long bookId;

    @Column(name = "book_title")
    private String bookTitle;

    @Column(name = "text_content")
    private String textContent;

    @Column(name = "note_content")
    private String noteContent;

    @Column(name = "color")
    private String color;

    @Column(name = "style")
    private String style;

    @Column(name = "chapter_title")
    private String chapterTitle;

    @Column(name = "primary_book_type")
    private String primaryBookType;

    @Column(name = "cover_updated_on")
    private Instant coverUpdatedOn;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
