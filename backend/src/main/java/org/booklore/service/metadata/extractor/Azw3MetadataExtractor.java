package org.booklore.service.metadata.extractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;

@Slf4j
@Component
public class Azw3MetadataExtractor extends MobiBaseMetadataExtractor {

    private static final long KF8_BOUNDARY = 0xFFFFFFFF;

    @Override
    protected String getFormatName() {
        return "AZW3";
    }

    @Override
    protected void processFormatSpecificHeader(RandomAccessFile raf, PalmDBRecord record0, MobiHeader header, int headerLength) throws IOException {
        // For KF8 (AZW3), check for KF8 boundary and resource index
        if (header.isKF8 && headerLength >= 232) {
            raf.seek(record0.offset + 16 + 192);
            long kf8Boundary = readInt(raf) & 0xFFFFFFFFL;
            if (kf8Boundary == KF8_BOUNDARY) {
                log.debug("KF8 boundary found at expected location");
            }
        }
    }
}
