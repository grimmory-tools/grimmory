package org.booklore.model.dto.response;

import org.booklore.model.enums.BookFileType;

public record BookConversionResponse(int acceptedCount, BookFileType targetFormat) {}
