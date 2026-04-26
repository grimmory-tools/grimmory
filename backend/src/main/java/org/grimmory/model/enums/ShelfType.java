package org.booklore.model.enums;

import lombok.Getter;

@Getter
public enum ShelfType {
    KOBO("Kobo", "pi pi-tablet");

    private final String name;
    private final String icon;

    ShelfType(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }
}
