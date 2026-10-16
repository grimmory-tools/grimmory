package org.booklore.service.browse;

import org.booklore.model.entity.AuthorEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class AuthorSearchSpecification {

    private AuthorSearchSpecification() {
    }

    public static Specification<AuthorEntity> matching(String query) {
        String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, criteriaQuery, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("asin")), pattern));
    }
}
