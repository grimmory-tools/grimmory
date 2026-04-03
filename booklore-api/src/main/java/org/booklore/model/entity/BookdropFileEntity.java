package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.LazyGroup;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "bookdrop_file", uniqueConstraints = {@UniqueConstraint(name = "uq_file_path", columnNames = {"file_path"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookdropFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_path", columnDefinition = "TEXT", nullable = false)
    private String filePath;

    @Column(name = "file_name", length = 512, nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private Status status = Status.PENDING_REVIEW;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("metadata")
    @Column(name = "original_metadata", columnDefinition = "JSON")
    private String originalMetadata;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @LazyGroup("metadata")
    @Column(name = "fetched_metadata", columnDefinition = "JSON")
    private String fetchedMetadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Status {
        PENDING_REVIEW,
        FINALIZED
    }
}
