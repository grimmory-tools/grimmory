package org.booklore.service.metadata;

import org.booklore.model.dto.BookMetadata;

import java.util.Collection;

@FunctionalInterface
interface FieldValueExtractorList {
    Collection<String> extract(BookMetadata metadata);
}
