package org.grimmory.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.convertor.KoboSpanMapJsonConverter;
import org.booklore.model.dto.kobo.KoboSpanPositionMap;

import java.time.Instant;

@Entity
@Table(name = "kobo_span_map",
        uniqueConstraints = @UniqueConstraint(name = "uk_kobo_span_map_book_file", columnNames = "book_file_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KoboSpanMapEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_file_id", nullable = false)
    private BookFileEntity bookFile;

    @Column(name = "file_hash", nullable = false, length = 128)
    private String fileHash;

    @Convert(converter = KoboSpanMapJsonConverter.class)
    @Column(name = "span_map_json", nullable = false, columnDefinition = "LONGTEXT")
    private KoboSpanPositionMap spanMap;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
