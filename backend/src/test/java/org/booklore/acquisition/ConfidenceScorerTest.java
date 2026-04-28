package org.booklore.acquisition;

import org.booklore.model.dto.acquisition.NzbResult;
import org.booklore.model.entity.WantedBookEntity;
import org.booklore.model.enums.WantedBookStatus;
import org.booklore.service.acquisition.ConfidenceScorer;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Table-driven unit tests for ConfidenceScorer — no mocking needed.
 *
 * Scoring algorithm recap:
 *   +60  title coverage > 0.7 (all wanted-title words appear in NZB title)
 *   +30  title coverage > 0.5
 *   +15  title coverage > 0.3
 *   +20  full normalized author name appears as substring in NZB title
 *   +25  ISBN-13 found in NZB title
 *   +15  ISBN-10 found in NZB title
 *   +5   epub/mobi/pdf/azw3 in NZB title (format bonus)
 *   -30  "audiobook" in NZB title
 *   -20  "abridged" in NZB title
 */
class ConfidenceScorerTest {

    private final ConfidenceScorer scorer = new ConfidenceScorer();

    // ─── Case 1: title + author match ─────────────────────────────────────────

    @Test
    void dune_withAuthor_matchesDuneHerbertEpub_atLeast75() {
        WantedBookEntity wanted = wanted("Dune", "Frank Herbert", null, null);
        NzbResult nzb = nzb("Dune Frank Herbert EPUB");

        int score = scorer.calculateConfidence(wanted, nzb);

        assertThat(score).isGreaterThanOrEqualTo(75);
        // title coverage=1.0 → +60, author "frank herbert" in nzb → +20, epub → +5 = 85
    }

    // ─── Case 2: wrong sequel — author last name only present, not full name ──

    @Test
    void dune_withAuthor_duneMessiahHerbert_lessThan75() {
        WantedBookEntity wanted = wanted("Dune", "Frank Herbert", null, null);
        NzbResult nzb = nzb("Dune Messiah Herbert EPUB");

        int score = scorer.calculateConfidence(wanted, nzb);

        // "frank herbert" is NOT a substring of "dune messiah herbert epub"
        // score = +60 (title) + 0 (no full author) + 5 (epub) = 65
        assertThat(score).isLessThan(75);
    }

    // ─── Case 3: ISBN-13 bonus ────────────────────────────────────────────────

    @Test
    void dune_withIsbn_nzbContainsIsbn_atLeast85() {
        WantedBookEntity wanted = wanted("Dune", "Frank Herbert", "9780441013593", null);
        NzbResult nzb = nzb("9780441013593 Dune Herbert");

        int score = scorer.calculateConfidence(wanted, nzb);

        // title coverage=1.0 → +60, isbn13 in nzb → +25 = 85
        assertThat(score).isGreaterThanOrEqualTo(85);
    }

    // ─── Case 4: The Hobbit + Tolkien ─────────────────────────────────────────

    @Test
    void theHobbit_withAuthorTolkien_matchesHobbitTolkienEpub_atLeast75() {
        WantedBookEntity wanted = wanted("The Hobbit", "Tolkien", null, null);
        NzbResult nzb = nzb("Hobbit Tolkien epub mobi");

        int score = scorer.calculateConfidence(wanted, nzb);

        // "the" removed → wantedTitle="hobbit", coverage=1.0 → +60
        // "tolkien" in nzb → +20, epub → +5 = 85
        assertThat(score).isGreaterThanOrEqualTo(75);
    }

    // ─── Case 5: audiobook penalty ────────────────────────────────────────────

    @Test
    void dune_audiobook_lessThan75() {
        WantedBookEntity wanted = wanted("Dune", "Frank Herbert", null, null);
        NzbResult nzb = nzb("Best Dune Audiobook Frank Herbert");

        int score = scorer.calculateConfidence(wanted, nzb);

        // title → +60, author → +20, audiobook penalty → -30 = 50
        assertThat(score).isLessThan(75);
    }

    // ─── Case 6: completely unrelated title ───────────────────────────────────

    @Test
    void dune_completelyUnrelated_lessThan30() {
        WantedBookEntity wanted = wanted("Dune", "Frank Herbert", null, null);
        NzbResult nzb = nzb("Completely Unrelated Title EPUB");

        int score = scorer.calculateConfidence(wanted, nzb);

        // "dune" not in NZB words → coverage=0 → +0, no author, +5 epub = 5
        assertThat(score).isLessThan(30);
    }

    // ─── Case 7: ISBN-10 bonus ────────────────────────────────────────────────

    @Test
    void isbn10_inNzb_addsPoints() {
        WantedBookEntity wanted = wanted("Dune", "Frank Herbert", null, "0441013597");
        NzbResult nzb = nzb("Dune Frank Herbert 0441013597 EPUB");

        int score = scorer.calculateConfidence(wanted, nzb);

        // +60 title + +20 author + +15 isbn10 + +5 epub = 100 (capped)
        assertThat(score).isGreaterThanOrEqualTo(85);
    }

    // ─── Case 8: abridged penalty ─────────────────────────────────────────────

    @Test
    void dune_abridged_reducesScore() {
        WantedBookEntity wanted = wanted("Dune", "Frank Herbert", null, null);
        NzbResult nzbNormal = nzb("Dune Frank Herbert EPUB");
        NzbResult nzbAbridged = nzb("Dune Frank Herbert EPUB Abridged");

        int normalScore = scorer.calculateConfidence(wanted, nzbNormal);
        int abridgedScore = scorer.calculateConfidence(wanted, nzbAbridged);

        assertThat(abridgedScore).isLessThan(normalScore);
    }

    // ─── Case 9: threshold test ───────────────────────────────────────────────

    @Test
    void meetsThreshold_trueAt75OrAbove() {
        assertThat(scorer.meetsThreshold(75)).isTrue();
        assertThat(scorer.meetsThreshold(100)).isTrue();
        assertThat(scorer.meetsThreshold(74)).isFalse();
        assertThat(scorer.meetsThreshold(0)).isFalse();
    }

    // ─── Case 10: null fields handled gracefully ──────────────────────────────

    @Test
    void nullAuthorAndIsbn_noNpe() {
        WantedBookEntity wanted = wanted("Dune", null, null, null);
        NzbResult nzb = nzb("Dune Frank Herbert EPUB");

        int score = scorer.calculateConfidence(wanted, nzb);

        assertThat(score).isGreaterThanOrEqualTo(60);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private WantedBookEntity wanted(String title, String author, String isbn13, String isbn10) {
        return WantedBookEntity.builder()
                .title(title)
                .author(author)
                .isbn13(isbn13)
                .isbn10(isbn10)
                .status(WantedBookStatus.WANTED)
                .addedAt(Instant.now())
                .build();
    }

    private NzbResult nzb(String title) {
        return new NzbResult(title, "https://example.com/nzb/123.nzb",
                5_242_880L, Instant.now(), 10, "TestIndexer");
    }
}
