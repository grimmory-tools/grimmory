package org.booklore.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

@Getter
public enum BookFileType {

    PDF(Set.of("pdf")),
    EPUB(Set.of("epub")),
    CBX(Set.of("cbz", "cbr", "cb7")),
    FB2(Set.of("fb2")),
    MOBI(Set.of("mobi")),
    AZW3(Set.of("azw3", "azw")),
    AUDIOBOOK(Set.of("m4b", "m4a", "mp3", "opus"));

    private final Set<String> extensions;

    BookFileType(Set<String> extensions) {
        this.extensions = extensions;
    }

    public boolean supports(String extension) {
        return extensions.contains(extension.toLowerCase());
    }

    public static Optional<BookFileType> fromExtension(String extension) {
        String ext = extension.toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.supports(ext))
                .findFirst();
    }
}
