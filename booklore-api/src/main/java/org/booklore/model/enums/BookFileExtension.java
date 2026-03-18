package org.booklore.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
@Getter
public enum BookFileExtension {
    PDF("pdf", BookFileType.PDF),
    EPUB("epub", BookFileType.EPUB),
    CBZ("cbz", BookFileType.CBX),
    CBR("cbr", BookFileType.CBX),
    CB7("cb7", BookFileType.CBX),
    MOBI("mobi", BookFileType.MOBI),
    AZW3("azw3", BookFileType.AZW3),
    AZW("azw", BookFileType.AZW3),
    FB2("fb2", BookFileType.FB2),
    M4B("m4b", BookFileType.AUDIOBOOK),
    M4A("m4a", BookFileType.AUDIOBOOK),
    MP3("mp3", BookFileType.AUDIOBOOK),
    OPUS("opus", BookFileType.AUDIOBOOK);

    private final String extension;
    private final BookFileType type;

    public static Optional<BookFileExtension> fromFileName(String fileName) {
        String lower = fileName.toLowerCase();
        return Arrays.stream(values())
                .filter(e -> lower.endsWith("." + e.extension))
                .findFirst();
    }
}
