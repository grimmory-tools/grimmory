package org.booklore.service.metadata.parser.hardcover;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class HardcoverMoodFilterTest {

    @Test
    @DisplayName("filterBasicMoods should return empty set for null input")
    void filterBasicMoods_nullInput_returnsEmptySet() {
        Set<String> result = HardcoverMoodFilter.filterBasicMoods(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterBasicMoods should return empty set for empty list")
    void filterBasicMoods_emptyList_returnsEmptySet() {
        Set<String> result = HardcoverMoodFilter.filterBasicMoods(Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterBasicMoods should only return recognized moods")
    void filterBasicMoods_mixedMoods_returnsOnlyRecognized() {
        List<String> moods = List.of("funny", "invalid-mood", "sad", "not-a-mood", "dark");

        Set<String> result = HardcoverMoodFilter.filterBasicMoods(moods);

        assertThat(result)
                .containsExactlyInAnyOrder("funny", "sad", "dark")
                .doesNotContain("invalid-mood", "not-a-mood");
    }

    @Test
    @DisplayName("filterBasicMoods should normalize case to lowercase")
    void filterBasicMoods_mixedCase_normalizesToLowercase() {
        List<String> moods = List.of("FUNNY", "Sad", "DaRk", "Emotional");

        Set<String> result = HardcoverMoodFilter.filterBasicMoods(moods);

        assertThat(result)
                .containsExactlyInAnyOrder("funny", "sad", "dark", "emotional");
    }

    @Test
    @DisplayName("filterBasicMoods should limit results to MAX_MOODS")
    void filterBasicMoods_tooManyMoods_limitsToMax() {
        List<String> moods = List.of(
                "funny", "sad", "dark", "emotional", "tense",
                "hopeful", "romantic", "scary", "mysterious", "reflective"
        );

        Set<String> result = HardcoverMoodFilter.filterBasicMoods(moods);

        assertThat(result).hasSize(HardcoverMoodFilter.MAX_MOODS);
    }

    @Test
    @DisplayName("filterBasicMoods should handle null entries in list")
    void filterBasicMoods_nullEntries_ignoresNulls() {
        List<String> moods = new ArrayList<>();
        moods.add("funny");
        moods.add(null);
        moods.add("sad");
        moods.add(null);

        Set<String> result = HardcoverMoodFilter.filterBasicMoods(moods);

        assertThat(result).containsExactlyInAnyOrder("funny", "sad");
    }

    @Test
    @DisplayName("filterBasicMoods should trim whitespace from moods")
    void filterBasicMoods_whitespace_trimsCorrectly() {
        List<String> moods = List.of("  funny  ", " sad", "dark ");

        Set<String> result = HardcoverMoodFilter.filterBasicMoods(moods);

        assertThat(result).containsExactlyInAnyOrder("funny", "sad", "dark");
    }

    @Test
    @DisplayName("filterMoodsWithCounts should return empty set for null cached_tags")
    void filterMoodsWithCounts_nullCachedTags_returnsEmptySet() {
        Set<String> result = HardcoverMoodFilter.filterMoodsWithCounts((Map<String, List<HardcoverCachedTag>>) null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterMoodsWithCounts should return empty set when no Mood category")
    void filterMoodsWithCounts_noMoodCategory_returnsEmptySet() {
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        cachedTags.put("Genre", List.of(createCachedTag("Fiction", 10)));

        Set<String> result = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterMoodsWithCounts should filter out low-vote moods")
    void filterMoodsWithCounts_mixedVotes_filtersLowVotes() {
        // Simulating "All Quiet on the Western Front" scenario
        // "sad" has 17 votes, "funny" has 4 votes
        // With max=17 and MIN_VOTE_RATIO=0.15, threshold is ~3
        // But "funny" at 4 votes should still be filtered if we use higher threshold
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        cachedTags.put("Mood", List.of(
                createCachedTag("sad", 17),
                createCachedTag("dark", 16),
                createCachedTag("emotional", 10),
                createCachedTag("funny", 4),    // Low relative to "sad"
                createCachedTag("hopeful", 1),  // Very low
                createCachedTag("scary", 1)     // Very low
        ));

        Set<String> result = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags);

        // Top moods should be included
        assertThat(result).contains("sad", "dark", "emotional");
        // Low-vote moods should be filtered
        assertThat(result).doesNotContain("hopeful", "scary");
    }

    @Test
    @DisplayName("filterMoodsWithCounts should respect MAX_MOODS limit")
    void filterMoodsWithCounts_manyHighVoteMoods_limitsToMax() {
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        List<HardcoverCachedTag> moodTags = new ArrayList<>();
        // Create 10 moods all with high vote counts
        for (int i = 0; i < 10; i++) {
            moodTags.add(createCachedTag("mood" + i, 20 - i));
        }
        cachedTags.put("Mood", moodTags);

        Set<String> result = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags);

        assertThat(result).hasSize(HardcoverMoodFilter.MAX_MOODS);
    }

    @Test
    @DisplayName("filterMoodsWithCounts should order moods by vote count (highest first)")
    void filterMoodsWithCounts_orderedByVoteCount() {
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        cachedTags.put("Mood", List.of(
                createCachedTag("emotional", 5),
                createCachedTag("sad", 15),
                createCachedTag("dark", 10)
        ));

        Set<String> result = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags);

        // Result should be LinkedHashSet preserving order
        List<String> resultList = new ArrayList<>(result);
        assertThat(resultList).containsExactly("sad", "dark", "emotional");
    }

    @Test
    @DisplayName("filterMoodsWithCounts should handle null vote counts")
    void filterMoodsWithCounts_nullVoteCounts_handlesGracefully() {
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        HardcoverCachedTag tagWithNullCount = createCachedTag("mysterious", null);
        cachedTags.put("Mood", List.of(
                createCachedTag("sad", 10),
                tagWithNullCount,
                createCachedTag("dark", 8)
        ));

        Set<String> result = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags);

        assertThat(result).containsExactlyInAnyOrder("sad", "dark");
        assertThat(result).doesNotContain("mysterious");
    }

    @Test
    @DisplayName("filterMoodsWithCounts should return empty when all votes are zero")
    void filterMoodsWithCounts_allZeroVotes_returnsEmpty() {
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        cachedTags.put("Mood", List.of(
                createCachedTag("sad", 0),
                createCachedTag("dark", 0)
        ));

        Set<String> result = HardcoverMoodFilter.filterMoodsWithCounts(cachedTags);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterGenresWithCounts should return filtered genres")
    void filterGenresWithCounts_validData_returnsFiltered() {
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        cachedTags.put("Genre", List.of(
                createCachedTag("Fiction", 10),
                createCachedTag("War", 8),
                createCachedTag("Classics", 5),
                createCachedTag("Comics", 0)  // Should be filtered out
        ));

        Set<String> result = HardcoverMoodFilter.filterGenresWithCounts(cachedTags);

        assertThat(result).containsExactlyInAnyOrder("Fiction", "War", "Classics");
        assertThat(result).doesNotContain("Comics");
    }

    @Test
    @DisplayName("filterTagsWithCounts should filter tags with low votes")
    void filterTagsWithCounts_mixedVotes_filtersLowVotes() {
        Map<String, List<HardcoverCachedTag>> cachedTags = new HashMap<>();
        cachedTags.put("Tag", List.of(
                createCachedTag("Loveable Characters", 13),
                createCachedTag("Strong Character Development", 9),
                createCachedTag("Plot driven", 1)  // Should be filtered (below threshold)
        ));

        Set<String> result = HardcoverMoodFilter.filterTagsWithCounts(cachedTags);

        assertThat(result).contains("Loveable Characters", "Strong Character Development");
        assertThat(result).doesNotContain("Plot driven");
    }

    @Test
    @DisplayName("normalizeMood should return null for null input")
    void normalizeMood_nullInput_returnsNull() {
        String result = HardcoverMoodFilter.normalizeMood(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("normalizeMood should lowercase and trim")
    void normalizeMood_mixedCaseWithWhitespace_normalizes() {
        String result = HardcoverMoodFilter.normalizeMood("  FuNnY  ");
        assertThat(result).isEqualTo("funny");
    }

    @Test
    @DisplayName("All recognized moods should be in the set")
    void recognizedMoods_containsExpectedMoods() {
        assertThat(HardcoverMoodFilter.RECOGNIZED_MOODS)
                .contains("adventurous", "challenging", "dark", "emotional", "funny",
                        "hopeful", "informative", "inspiring", "lighthearted", "mysterious",
                        "reflective", "relaxing", "romantic", "sad", "scary", "tense");
    }

    /**
     * Helper method to create a CachedTag for testing.
     */
    private HardcoverCachedTag createCachedTag(String tag, Integer count) {
        HardcoverCachedTag cachedTag = new HardcoverCachedTag();
        cachedTag.setTag(tag);
        cachedTag.setCount(count);
        return cachedTag;
    }
}
