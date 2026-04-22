package org.booklore.model.entity;

import org.booklore.util.BookUtils;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BookMetadataEntityTest {

    @Test
    void updateSearchText_populatesSearchText() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Jo Nesbø Book");
        metadata.setSubtitle("Murder Mystery");
        metadata.setAuthors(List.of(AuthorEntity.builder().name("Jo Nesbø").build()));

        metadata.updateSearchText();

        String searchText = metadata.getSearchText();
        assertNotNull(searchText);
        assertTrue(searchText.contains("jo nesbo book"));
        assertTrue(searchText.contains("murder mystery"));
        assertTrue(searchText.contains("jo nesbo"));
    }

    @Test
    void updateSearchText_normalizesAuthorWithDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("The Snowman");
        metadata.setAuthors(List.of(AuthorEntity.builder().name("Jo Nesbø").build()));

        metadata.updateSearchText();

        String searchText = metadata.getSearchText();
        assertNotNull(searchText);
        // Verify that 'ø' is normalized to 'o'
        assertTrue(searchText.contains("nesbo"), "Should contain 'nesbo': " + searchText);
        assertFalse(searchText.contains("ø"), "Should not contain 'ø': " + searchText);
    }

    @Test
    void updateSearchText_handlesFrenchGermanAndSpanishDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Müller's Café");
        metadata.setSubtitle("À la française");
        metadata.setSeriesName("José's Stories");
        metadata.setAuthors(List.of(
            AuthorEntity.builder().name("François Müller").build(),
            AuthorEntity.builder().name("José García").build()
        ));

        metadata.updateSearchText();

        String searchText = metadata.getSearchText();
        assertNotNull(searchText);
        
        assertTrue(searchText.contains("muller"), "Should contain 'muller': " + searchText);
        assertTrue(searchText.contains("cafe"), "Should contain 'cafe': " + searchText);
        assertTrue(searchText.contains("a la francaise"), "Should contain 'a la francaise': " + searchText);
        assertTrue(searchText.contains("jose"), "Should contain 'jose': " + searchText);
        assertTrue(searchText.contains("garcia"), "Should contain 'garcia': " + searchText);
        assertTrue(searchText.contains("francois muller"), "Should contain 'francois muller': " + searchText);
        
        assertFalse(searchText.contains("ü"), "Should not contain 'ü': " + searchText);
        assertFalse(searchText.contains("é"), "Should not contain 'é': " + searchText);
        assertFalse(searchText.contains("à"), "Should not contain 'à': " + searchText);
        assertFalse(searchText.contains("í"), "Should not contain 'í': " + searchText);
    }

    @Test
    void updateSearchText_trimsLeadingAndTrailingWhitespace() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("  The Snowman  ");
        metadata.setSubtitle("  A Mystery  ");
        metadata.setPublisher("  Harvill Secker  ");
        metadata.setSeriesName("  Harry Hole  ");
        metadata.setLanguage("  en  ");
        metadata.setIsbn13("  9780099520276  ");
        metadata.setIsbn10("  0099520273  ");
        metadata.setAsin("  B003GK21A8  ");
        metadata.setGoodreadsId("  12345  ");
        metadata.setHardcoverId("  hc-1  ");
        metadata.setHardcoverBookId("  hcb-1  ");
        metadata.setGoogleId("  g-1  ");
        metadata.setComicvineId("  cv-1  ");
        metadata.setLubimyczytacId("  lub-1  ");
        metadata.setRanobedbId("  ran-1  ");
        metadata.setAudibleId("  aud-1  ");
        metadata.setContentRating("  PG-13  ");
        metadata.setNarrator("  John Smith  ");

        metadata.updateSearchText();

        assertEquals("The Snowman", metadata.getTitle());
        assertEquals("A Mystery", metadata.getSubtitle());
        assertEquals("Harvill Secker", metadata.getPublisher());
        assertEquals("Harry Hole", metadata.getSeriesName());
        assertEquals("en", metadata.getLanguage());
        assertEquals("9780099520276", metadata.getIsbn13());
        assertEquals("0099520273", metadata.getIsbn10());
        assertEquals("B003GK21A8", metadata.getAsin());
        assertEquals("12345", metadata.getGoodreadsId());
        assertEquals("hc-1", metadata.getHardcoverId());
        assertEquals("hcb-1", metadata.getHardcoverBookId());
        assertEquals("g-1", metadata.getGoogleId());
        assertEquals("cv-1", metadata.getComicvineId());
        assertEquals("lub-1", metadata.getLubimyczytacId());
        assertEquals("ran-1", metadata.getRanobedbId());
        assertEquals("aud-1", metadata.getAudibleId());
        assertEquals("PG-13", metadata.getContentRating());
        assertEquals("John Smith", metadata.getNarrator());
    }

    @Test
    void updateSearchText_blanksAndWhitespaceOnlyBecomeNull() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Valid Title");
        metadata.setSubtitle("   ");
        metadata.setPublisher("");
        metadata.setSeriesName("  \t  ");
        metadata.setLanguage("  ");
        metadata.setNarrator("\t");

        metadata.updateSearchText();

        assertEquals("Valid Title", metadata.getTitle());
        assertNull(metadata.getSubtitle());
        assertNull(metadata.getPublisher());
        assertNull(metadata.getSeriesName());
        assertNull(metadata.getLanguage());
        assertNull(metadata.getNarrator());
    }

    @Test
    void updateSearchText_preservesNulls() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Valid Title");

        metadata.updateSearchText();

        assertEquals("Valid Title", metadata.getTitle());
        assertNull(metadata.getSubtitle());
        assertNull(metadata.getPublisher());
        assertNull(metadata.getSeriesName());
        assertNull(metadata.getNarrator());
    }

    @Test
    void updateSearchText_doesNotTrimDescription() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("Valid Title");
        metadata.setDescription("  Some description with leading/trailing spaces  ");

        metadata.updateSearchText();

        assertEquals("  Some description with leading/trailing spaces  ", metadata.getDescription());
    }

    @Test
    void searchSimulation_withDiacritics() {
        BookMetadataEntity metadata = new BookMetadataEntity();
        metadata.setTitle("The Bat");
        metadata.setAuthors(List.of(AuthorEntity.builder().name("Jo Nesbø").build()));
        metadata.updateSearchText();
        
        String storedSearchText = metadata.getSearchText();
        
        String searchQuery1 = BookUtils.normalizeForSearch("nesbo"); // without ø
        String searchQuery2 = BookUtils.normalizeForSearch("Nesbø"); // with ø
        String searchQuery3 = BookUtils.normalizeForSearch("NESBO"); // uppercase
        String searchQuery4 = BookUtils.normalizeForSearch("Jo Nesbø"); // full name with ø
        
        assertEquals(searchQuery1, searchQuery2, "Queries with and without diacritics should match");
        assertEquals(searchQuery1, searchQuery3, "Case should not matter");
        
        // Simulate LIKE '%query%' - all searches should find the book
        assertTrue(storedSearchText.contains(searchQuery1), 
            "Search 'nesbo' should match stored text: " + storedSearchText);
        assertTrue(storedSearchText.contains(searchQuery2), 
            "Search 'Nesbø' should match stored text: " + storedSearchText);
        assertTrue(storedSearchText.contains(searchQuery3), 
            "Search 'NESBO' should match stored text: " + storedSearchText);
        assertTrue(storedSearchText.contains(searchQuery4), 
            "Search 'Jo Nesbø' should match stored text: " + storedSearchText);
    }
}
