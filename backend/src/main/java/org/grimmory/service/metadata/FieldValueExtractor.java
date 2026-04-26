package org.grimmory.service.metadata;

import org.grimmory.model.dto.BookMetadata;

@FunctionalInterface
interface FieldValueExtractor {
    String extract(BookMetadata metadata);
}
