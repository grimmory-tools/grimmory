package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kobo_removed_books_tracking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KoboDeletedBookProgressEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false)
    private String snapshotId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "book_id_synced", nullable = false)
    private Long bookIdSynced;
}
