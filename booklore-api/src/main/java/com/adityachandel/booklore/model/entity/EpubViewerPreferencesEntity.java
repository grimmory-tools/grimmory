package com.adityachandel.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "epub_viewer_preference", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "book_id"})
})
public class EpubViewerPreferencesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "theme")
    private String theme;

    @Column(name = "font")
    private String font;

    @Column(name = "font_size")
    private Integer fontSize;

    @Column(name = "letter_spacing")
    private Float letterSpacing;

    @Column(name = "line_height")
    private Float lineHeight;

    @Column(name = "flow")
    private String flow;
}