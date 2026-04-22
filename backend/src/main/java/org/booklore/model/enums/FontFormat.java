package org.booklore.model.enums;

import lombok.Getter;

@Getter
public enum FontFormat {
    TTF("font/ttf", ".ttf"),
    OTF("font/otf", ".otf"),
    WOFF("font/woff", ".woff"),
    WOFF2("font/woff2", ".woff2");

    private final String mimeType;
    private final String extension;

    FontFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static FontFormat fromExtension(String extension) {
        String normalizedExt = extension.toLowerCase();
        if (!normalizedExt.startsWith(".")) {
            normalizedExt = "." + normalizedExt;
        }
        for (FontFormat format : values()) {
            if (format.extension.equals(normalizedExt)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported font format: " + extension);
    }

    public static FontFormat fromMimeType(String mimeType) {
        for (FontFormat format : values()) {
            if (format.mimeType.equals(mimeType)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unsupported MIME type: " + mimeType);
    }

    public static boolean isSupportedExtension(String extension) {
        String normalizedExt = extension.toLowerCase();
        if (!normalizedExt.startsWith(".")) {
            normalizedExt = "." + normalizedExt;
        }
        for (FontFormat format : values()) {
            if (format.extension.equals(normalizedExt)) {
                return true;
            }
        }
        return false;
    }
}
