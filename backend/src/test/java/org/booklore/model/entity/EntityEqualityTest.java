package org.booklore.model.entity;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityEqualityTest {

    @Test
    void authorEntity_shouldBeEqual_whenIdsAreSame() {
        // 1. Same ID, Different Names -> Should be EQUAL
        AuthorEntity a1 = AuthorEntity.builder().id(1L).name("Author A").build();
        AuthorEntity a2 = AuthorEntity.builder().id(1L).name("Author B").build();

        assertEquals(a1, a2, "Entities with same ID should be equal");
        assertEquals(a1.hashCode(), a2.hashCode(), "HashCodes must match for equal objects");
    }

    @Test
    void authorEntity_shouldNotBeEqual_whenIdsAreDifferent() {
        // 2. Different ID, Same Name -> Should be DIFFERENT
        AuthorEntity a1 = AuthorEntity.builder().id(1L).name("Author A").build();
        AuthorEntity a2 = AuthorEntity.builder().id(2L).name("Author A").build();

        assertNotEquals(a1, a2, "Entities with different IDs should not be equal");
    }

    @Test
    void set_shouldDeduplicate_basedOnId() {
        // 3. Set behavior test (The most important practical test)
        Set<AuthorEntity> set = new HashSet<>();

        AuthorEntity a1 = AuthorEntity.builder().id(100L).name("John").build();
        AuthorEntity a2 = AuthorEntity.builder().id(100L).name("John Updated").build(); // Same ID
        AuthorEntity a3 = AuthorEntity.builder().id(200L).name("Jane").build();

        set.add(a1);
        set.add(a2); // Should replace a1 or be ignored depending on Set impl, but size should stay 1
        set.add(a3);

        assertEquals(2, set.size(), "Set should contain only 2 unique entities based on ID");
        assertTrue(set.contains(a1));
        assertTrue(set.contains(a3));
    }

    @Test
    void categoryEntity_shouldBeEqual_whenIdsAreSame() {
        CategoryEntity c1 = CategoryEntity.builder().id(1L).name("Fiction").build();
        CategoryEntity c2 = CategoryEntity.builder().id(1L).name("Non-Fiction").build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void moodEntity_shouldBeEqual_whenIdsAreSame() {
        MoodEntity m1 = MoodEntity.builder().id(1L).name("Happy").build();
        MoodEntity m2 = MoodEntity.builder().id(1L).name("Sad").build();

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void tagEntity_shouldBeEqual_whenIdsAreSame() {
        TagEntity t1 = TagEntity.builder().id(1L).name("Adventure").build();
        TagEntity t2 = TagEntity.builder().id(1L).name("Mystery").build();

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void unsavedEntities_withNullIds_shouldNotBeEqual() {
        AuthorEntity a1 = AuthorEntity.builder().name("John").build();
        AuthorEntity a2 = AuthorEntity.builder().name("Jane").build();

        assertNotEquals(a1, a2, "Unsaved entities with null IDs should not be equal");
    }

    @Test
    void sameInstance_shouldBeEqual() {
        AuthorEntity a1 = AuthorEntity.builder().id(1L).name("John").build();

        assertEquals(a1, a1, "Same instance should be equal to itself");
    }

    @Test
    void testEntityFactory_createsEntitiesWithUniqueIds() {
        AuthorEntity a1 = TestEntityFactory.createAuthor("Author 1");
        AuthorEntity a2 = TestEntityFactory.createAuthor("Author 2");

        assertNotNull(a1.getId(), "Factory should assign an ID");
        assertNotNull(a2.getId(), "Factory should assign an ID");
        assertNotEquals(a1.getId(), a2.getId(), "Factory should assign unique IDs");
        assertNotEquals(a1, a2, "Entities with different IDs should not be equal");
    }
}