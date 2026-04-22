package org.booklore.model.dto;

import java.time.LocalDateTime;

public record ReleaseNote(String version, String name, String changelog, String url, LocalDateTime publishedAt) {
}
