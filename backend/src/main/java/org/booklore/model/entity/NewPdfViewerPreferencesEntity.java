package org.booklore.model.entity;

import org.booklore.model.enums.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "new_pdf_viewer_preference", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "book_id"})
})
public class NewPdfViewerPreferencesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "spread")
    @Enumerated(EnumType.STRING)
    private NewPdfPageSpread pageSpread;

    @Column(name = "view_mode")
    @Enumerated(EnumType.STRING)
    private NewPdfPageViewMode pageViewMode;

    @Column(name = "fit_mode")
    @Enumerated(EnumType.STRING)
    private NewPdfPageFitMode fitMode;

    @Column(name = "scroll_mode")
    @Enumerated(EnumType.STRING)
    private NewPdfPageScrollMode scrollMode;

    @Column(name = "background_color")
    @Enumerated(EnumType.STRING)
    private NewPdfBackgroundColor backgroundColor;
}