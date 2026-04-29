package org.booklore.model.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorEntitySortNameTest {

    @Test
    void updateSortName_withSpaces_reordersToSurnameFirst() {
        AuthorEntity author = new AuthorEntity();
        author.setName("J.R.R. Tolkien");
        author.updateSortName();
        assertEquals("Tolkien, J.R.R.", author.getSortName());
    }

    @Test
    void updateSortName_noSpaces_keepsOriginal() {
        AuthorEntity author = new AuthorEntity();
        author.setName("Madonna");
        author.updateSortName();
        assertEquals("Madonna", author.getSortName());
    }

    @Test
    void updateSortName_null_setsNull() {
        AuthorEntity author = new AuthorEntity();
        author.setName(null);
        author.updateSortName();
        assertNull(author.getSortName());
    }

    @Test
    void updateSortName_extraSpaces_trimsCorrectly() {
        AuthorEntity author = new AuthorEntity();
        author.setName("  Vincent van Gogh  ");
        author.updateSortName();
        assertEquals("Gogh, Vincent van", author.getSortName());
    }

    @Test
    void updateSortName_hyphenatedName_handlesAsSingleWord() {
        AuthorEntity author = new AuthorEntity();
        author.setName("Jean-Paul Sartre");
        author.updateSortName();
        assertEquals("Sartre, Jean-Paul", author.getSortName());
    }

    @Test
    void updateSortName_blank_setsEmpty() {
        AuthorEntity author = new AuthorEntity();
        author.setName("   ");
        author.updateSortName();
        assertEquals("", author.getSortName());
    }
}
