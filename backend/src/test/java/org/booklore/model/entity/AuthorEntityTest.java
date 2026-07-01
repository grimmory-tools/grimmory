package org.booklore.model.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorEntityTest {

    @Test
    void computesSortNameOnSaveWhenUnlocked() {
        AuthorEntity author = AuthorEntity.builder().name("George Orwell").build();

        author.computeSortName();

        assertThat(author.getSortName()).isEqualTo("Orwell, George");
    }

    @Test
    void preservesManualOverrideWhenLocked() {
        AuthorEntity author = AuthorEntity.builder()
                .name("George Orwell")
                .sortName("Orwell the Great")
                .sortNameLocked(true)
                .build();

        author.computeSortName();

        assertThat(author.getSortName()).isEqualTo("Orwell the Great");
    }

    @Test
    void leavesSortNameNullForNullName() {
        AuthorEntity author = AuthorEntity.builder().build();

        author.computeSortName();

        assertThat(author.getSortName()).isNull();
    }

    @Test
    void computesBlankSortNameForBlankName() {
        AuthorEntity author = AuthorEntity.builder().name("   ").build();

        author.computeSortName();

        assertThat(author.getSortName()).isEmpty();
    }

    @Test
    void recomputesAfterUnlocking() {
        AuthorEntity author = AuthorEntity.builder()
                .name("George Orwell")
                .sortName("Orwell the Great")
                .sortNameLocked(true)
                .build();

        author.setSortNameLocked(false);
        author.computeSortName();

        assertThat(author.getSortName()).isEqualTo("Orwell, George");
    }
}
