package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Cacheable
@NaturalIdCache(region = "org.booklore.model.entity.MoodEntity_NaturalId")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mood")
public class MoodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NaturalId(mutable = false)
    @Setter(AccessLevel.NONE)
    @Column(name = "name", nullable = false, unique = true, updatable = false)
    private String name;

    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    @ManyToMany(mappedBy = "moods", fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    @Builder.Default
    private Set<BookMetadataEntity> bookMetadataEntityList = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MoodEntity that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

