package org.booklore.model.entity;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EntityEqualityTest {

    @Test
    void authorEntity_shouldBeEqual_whenNamesAreSame() {
        // NaturalId equality: Same Name, Different ID -> Should be EQUAL
        AuthorEntity a1 = AuthorEntity.builder().id(1L).name("Author A").build();
        AuthorEntity a2 = AuthorEntity.builder().id(2L).name("Author A").build();

        assertEquals(a1, a2, "Entities with same name should be equal (NaturalId)");
        assertEquals(a1.hashCode(), a2.hashCode(), "HashCodes must match for equal objects");
    }

    @Test
    void authorEntity_shouldNotBeEqual_whenNamesAreDifferent() {
        // NaturalId equality: Different Name, Same ID -> Should be DIFFERENT
        AuthorEntity a1 = AuthorEntity.builder().id(1L).name("Author A").build();
        AuthorEntity a2 = AuthorEntity.builder().id(1L).name("Author B").build();

        assertNotEquals(a1, a2, "Entities with different names should not be equal");
    }

    @Test
    void bookEntity_shouldBeEqual_whenIdsAreSame() {
        // Surrogate ID equality
        BookEntity b1 = BookEntity.builder().id(1L).metadataMatchScore(0.9f).build();
        BookEntity b2 = BookEntity.builder().id(1L).metadataMatchScore(0.8f).build();

        assertEquals(b1, b2, "Entities with same ID should be equal");
        assertEquals(b1.hashCode(), b2.hashCode());
    }

    @Test
    void bookMetadataEntity_shouldBeEqual_whenIdsAreSame() {
        // Surrogate ID equality (@MapsId)
        BookMetadataEntity m1 = BookMetadataEntity.builder().bookId(1L).title("Title A").build();
        BookMetadataEntity m2 = BookMetadataEntity.builder().bookId(1L).title("Title B").build();

        assertEquals(m1, m2, "Entities with same ID should be equal");
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void set_shouldDeduplicate_basedOnNaturalId() {
        Set<AuthorEntity> set = new HashSet<>();

        AuthorEntity a1 = AuthorEntity.builder().id(1L).name("John").build();
        AuthorEntity a2 = AuthorEntity.builder().id(2L).name("John").build(); // Same Name, Different ID
        AuthorEntity a3 = AuthorEntity.builder().id(3L).name("Jane").build();

        set.add(a1);
        set.add(a2); 
        set.add(a3);

        assertEquals(2, set.size(), "Set should contain only 2 unique entities based on Name");
    }

    @Test
    void categoryEntity_shouldBeEqual_whenNamesAreSame() {
        CategoryEntity c1 = CategoryEntity.builder().id(1L).name("Fiction").build();
        CategoryEntity c2 = CategoryEntity.builder().id(2L).name("Fiction").build();

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    void moodEntity_shouldBeEqual_whenNamesAreSame() {
        MoodEntity m1 = MoodEntity.builder().id(1L).name("Happy").build();
        MoodEntity m2 = MoodEntity.builder().id(2L).name("Happy").build();

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }

    @Test
    void tagEntity_shouldBeEqual_whenNamesAreSame() {
        TagEntity t1 = TagEntity.builder().id(1L).name("Adventure").build();
        TagEntity t2 = TagEntity.builder().id(2L).name("Adventure").build();

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    void unsavedEntities_withNullIds_shouldNotBeEqual_ifNamesDifferent() {
        AuthorEntity a1 = AuthorEntity.builder().name("John").build();
        AuthorEntity a2 = AuthorEntity.builder().name("Jane").build();

        assertNotEquals(a1, a2);
    }

    @Test
    void sameInstance_shouldBeEqual() {
        AuthorEntity a1 = AuthorEntity.builder().id(1L).name("John").build();

        assertEquals(a1, a1, "Same instance should be equal to itself");
    }

    @Test
    void testEntityFactory_createsEntitiesWithUniqueNames() {
        AuthorEntity a1 = TestEntityFactory.createAuthor("Author 1");
        AuthorEntity a2 = TestEntityFactory.createAuthor("Author 2");

        assertNotEquals(a1.getName(), a2.getName());
        assertNotEquals(a1, a2, "Entities with different names should not be equal");
    }
}