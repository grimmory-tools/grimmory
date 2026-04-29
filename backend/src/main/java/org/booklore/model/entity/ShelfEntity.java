package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.convertor.SortConverter;
import org.booklore.model.dto.Sort;
import org.booklore.model.enums.IconType;
import org.hibernate.Hibernate;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Formula;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "shelf")
public class ShelfEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @Column(name = "name", nullable = false)
    private String name;

    @Convert(converter = SortConverter.class)
    private Sort sort;

    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "icon_type")
    private IconType iconType;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @Formula("(SELECT COUNT(*) FROM book_shelf_mapping bsm WHERE bsm.shelf_id = id)")
    private int bookCount;

    @BatchSize(size = 20)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "book_shelf_mapping",
            joinColumns = {@JoinColumn(name = "shelf_id")},
            inverseJoinColumns = {@JoinColumn(name = "book_id")}
    )
    @Builder.Default
    private Set<BookEntity> bookEntities = new HashSet<>();

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ShelfEntity that = (ShelfEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}