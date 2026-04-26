package org.grimmory.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "book_marks")
public class BookMarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @Column(name = "book_id", insertable = false, updatable = false)
    private Long bookId;

    // For EPUB bookmarks
    @Column(name = "cfi", length = 1000)
    private String cfi;

    // For audiobook bookmarks
    @Column(name = "position_ms")
    private Long positionMs;

    @Column(name = "track_index")
    private Integer trackIndex;

    // For PDF bookmarks
    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "title")
    private String title;

    @Column(name = "color")
    private String color;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "priority")
    private Integer priority;

    @jakarta.persistence.Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
