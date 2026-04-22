package org.booklore.model.entity;

import java.util.concurrent.atomic.AtomicLong;

public class TestEntityFactory {

    private static final AtomicLong idCounter = new AtomicLong(1);

    public static AuthorEntity createAuthor(String name) {
        return AuthorEntity.builder()
                .id(idCounter.getAndIncrement())
                .name(name)
                .build();
    }

    public static CategoryEntity createCategory(String name) {
        return CategoryEntity.builder()
                .id(idCounter.getAndIncrement())
                .name(name)
                .build();
    }

    public static MoodEntity createMood(String name) {
        return MoodEntity.builder()
                .id(idCounter.getAndIncrement())
                .name(name)
                .build();
    }

    public static TagEntity createTag(String name) {
        return TagEntity.builder()
                .id(idCounter.getAndIncrement())
                .name(name)
                .build();
    }
}