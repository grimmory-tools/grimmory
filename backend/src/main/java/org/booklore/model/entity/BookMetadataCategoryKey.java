package org.booklore.model.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookMetadataCategoryKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long bookId;
    private Long categoryId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookMetadataCategoryKey that)) return false;
        return Objects.equals(bookId, that.bookId) && Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, categoryId);
    }
}
