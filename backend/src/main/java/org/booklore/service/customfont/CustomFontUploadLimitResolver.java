package org.booklore.service.customfont;

import lombok.RequiredArgsConstructor;
import org.booklore.config.AppProperties;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@RequiredArgsConstructor
public class CustomFontUploadLimitResolver {

    private static final int DEFAULT_MAX_FILE_SIZE_MB = 50;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final AppProperties appProperties;
    private final MultipartProperties multipartProperties;

    public int getMaxFileSizeMb() {
        int configuredMaxFileSizeMb = getConfiguredMaxFileSizeMb();
        long configuredMaxFileSizeBytes = configuredMaxFileSizeMb * BYTES_PER_MB;
        long effectiveMaxFileSizeBytes = Math.min(configuredMaxFileSizeBytes, getMultipartCeilingBytes());

        return Math.max(1, (int) (effectiveMaxFileSizeBytes / BYTES_PER_MB));
    }

    private int getConfiguredMaxFileSizeMb() {
        AppProperties.CustomFont customFont = appProperties.getCustomFont();
        if (customFont == null || customFont.getMaxFileSizeMb() <= 0) {
            return DEFAULT_MAX_FILE_SIZE_MB;
        }
        return customFont.getMaxFileSizeMb();
    }

    private long getMultipartCeilingBytes() {
        return Math.min(
                toPositiveBytes(multipartProperties.getMaxFileSize()),
                toPositiveBytes(multipartProperties.getMaxRequestSize())
        );
    }

    private long toPositiveBytes(DataSize dataSize) {
        if (dataSize == null || dataSize.toBytes() <= 0) {
            return Long.MAX_VALUE;
        }
        return dataSize.toBytes();
    }
}
