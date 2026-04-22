package org.booklore.service.metadata.extractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MobiMetadataExtractor extends MobiBaseMetadataExtractor {

    @Override
    protected String getFormatName() {
        return "MOBI";
    }
}
