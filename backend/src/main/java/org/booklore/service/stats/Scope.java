package org.booklore.service.stats;

import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneId;

record Scope(long userId, Specification<BookEntity> matching, ZoneId zone, Instant now) {
}
