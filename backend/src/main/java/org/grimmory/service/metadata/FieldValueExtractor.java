package org.booklore.service.metadata;

import org.booklore.model.dto.BookMetadata;

@FunctionalInterface
interface FieldValueExtractor {
    String extract(BookMetadata metadata);
}
