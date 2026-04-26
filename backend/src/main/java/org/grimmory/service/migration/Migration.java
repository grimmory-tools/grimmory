package org.grimmory.service.migration;

public interface Migration {
    String getKey();

    String getDescription();

    void execute();
}