package org.booklore.model.dto.response;

import org.booklore.model.enums.BookFileType;

import java.util.List;

public record BookConversionCapabilityResponse(boolean available, List<BookFileType> supportedTargetFormats) {}
