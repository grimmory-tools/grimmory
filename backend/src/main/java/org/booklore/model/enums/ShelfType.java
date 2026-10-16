package org.booklore.model.enums;

import lombok.Getter;

@Getter
public enum ShelfType {
    KOBO("Kobo", "kobo-white", IconType.CUSTOM_SVG);

    private final String name;
    private final String icon;
    private final IconType iconType;

    ShelfType(String name, String icon, IconType iconType) {
        this.name = name;
        this.icon = icon;
        this.iconType = iconType;
    }
}
