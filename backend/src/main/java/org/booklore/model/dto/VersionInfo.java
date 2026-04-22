package org.booklore.model.dto;


import lombok.Getter;


@Getter
public class VersionInfo {
    private final String current;
    private final String latest;

    public VersionInfo(String current, String latest) {
        this.current = current;
        this.latest = latest;
    }
}
