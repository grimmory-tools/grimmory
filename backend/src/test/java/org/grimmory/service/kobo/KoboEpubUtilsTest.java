package org.booklore.service.kobo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KoboEpubUtilsTest {

    @Test
    void normalizeHref_returnsNullForNullHref() {
        assertNull(KoboEpubUtils.normalizeHref(null));
    }

    @Test
    void decodeHrefPath_preservesMalformedPathWhenUriParsingFails() {
        assertEquals("chapter%zz.xhtml", KoboEpubUtils.decodeHrefPath("chapter%zz.xhtml"));
    }

    @Test
    void normalizeHref_trimsLeadingSlashFromDecodedHref() {
        assertEquals("OPS/chapter 1.xhtml", KoboEpubUtils.normalizeHref("/OPS/chapter%201.xhtml"));
    }
}
