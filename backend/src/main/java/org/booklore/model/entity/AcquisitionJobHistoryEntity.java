package org.booklore.model.entity;

import org.booklore.model.enums.JobHistoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "acquisition_job_history")
public class AcquisitionJobHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wanted_book_id", nullable = false)
    private WantedBookEntity wantedBook;

    @Column(name = "indexer_id")
    private Long indexerId;

    @Column(name = "nzb_title", length = 1000)
    private String nzbTitle;

    @Column(name = "nzb_url", length = 2000)
    private String nzbUrl;

    @Column(name = "confidence")
    private Integer confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private JobHistoryStatus status;

    @Column(name = "attempted_at")
    private Instant attemptedAt;
}
