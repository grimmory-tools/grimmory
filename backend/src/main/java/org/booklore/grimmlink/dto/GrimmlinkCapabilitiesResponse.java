package org.booklore.grimmlink.dto;

public record GrimmlinkCapabilitiesResponse(
        String apiVersion,
        boolean webUiProgress,
        boolean progressSync,
        boolean pdfBridge,
        boolean readingSessions,
        boolean metadataSync,
        boolean shelves) {
}
