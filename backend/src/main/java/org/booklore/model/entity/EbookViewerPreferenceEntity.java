package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ebook_viewer_preference", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "book_id"})
})
public class EbookViewerPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "font_family", nullable = false)
    private String fontFamily;

    @Column(name = "font_size", nullable = false)
    private Integer fontSize;

    @Column(name = "gap", nullable = false)
    private Float gap;

    @Column(name = "hyphenate", nullable = false)
    private Boolean hyphenate;

    @Column(name = "is_dark", nullable = false)
    private Boolean isDark;

    @Column(name = "justify", nullable = false)
    private Boolean justify;

    @Column(name = "line_height", nullable = false)
    private Float lineHeight;

    @Column(name = "max_block_size", nullable = false)
    private Integer maxBlockSize;

    @Column(name = "max_column_count", nullable = false)
    private Integer maxColumnCount;

    @Column(name = "max_inline_size", nullable = false)
    private Integer maxInlineSize;

    @Column(name = "theme", nullable = false)
    private String theme;

    @Column(name = "flow", nullable = false)
    private String flow;
}

