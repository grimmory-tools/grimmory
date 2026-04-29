package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;

import java.util.Objects;

@Entity
@Cacheable
@NaturalIdCache(region = "org.booklore.model.entity.AppSettingEntity_NaturalId")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@Table(name = "app_settings")
@Getter
@Setter
public class AppSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NaturalId
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "val", nullable = false, columnDefinition = "TEXT")
    private String val;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppSettingEntity that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
