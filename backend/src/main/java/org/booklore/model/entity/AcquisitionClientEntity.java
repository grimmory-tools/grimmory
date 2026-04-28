package org.booklore.model.entity;

import org.booklore.model.enums.AcquisitionClientType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "acquisition_client")
public class AcquisitionClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private AcquisitionClientType type;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "category")
    private String category;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;
}
