package org.booklore.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShelfTypeTest {

    @Test
    void koboShelfUsesTheBundledKoboIcon() {
        assertEquals("kobo-white", ShelfType.KOBO.getIcon());
        assertEquals(IconType.CUSTOM_SVG, ShelfType.KOBO.getIconType());
    }
}
