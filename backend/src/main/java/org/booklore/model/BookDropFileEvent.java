package org.booklore.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

@Getter
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class BookDropFileEvent {
    private final Path file;
    private final WatchEvent.Kind<?> kind;
}
