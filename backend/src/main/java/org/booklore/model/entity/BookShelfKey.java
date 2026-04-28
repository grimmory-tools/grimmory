package org.booklore.model.entity;

import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.AccessLevel;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class BookShelfKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long bookId;
    private final Long shelfId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookShelfKey that)) return false;
        return Objects.equals(bookId, that.bookId) && Objects.equals(shelfId, that.shelfId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, shelfId);
    }
}