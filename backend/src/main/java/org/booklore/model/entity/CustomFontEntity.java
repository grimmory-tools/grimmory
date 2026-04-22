package org.booklore.model.entity;

import org.booklore.model.enums.FontFormat;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "custom_font")
public class CustomFontEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(name = "font_name", nullable = false)
    private String fontName;

    @Column(name = "file_name", nullable = false, unique = true)
    private String fileName;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private FontFormat format;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
