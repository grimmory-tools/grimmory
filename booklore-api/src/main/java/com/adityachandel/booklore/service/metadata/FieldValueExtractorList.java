package com.adityachandel.booklore.service.metadata;

import com.adityachandel.booklore.model.dto.BookMetadata;

import java.util.Set;

@FunctionalInterface
interface FieldValueExtractorList {
    Set<String> extract(BookMetadata metadata);
}
