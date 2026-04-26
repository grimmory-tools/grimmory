package org.grimmory.util;

import org.grimmory.model.MetadataClearFlags;
import org.grimmory.model.dto.BookMetadata;
import org.grimmory.model.entity.AuthorEntity;
import org.grimmory.model.entity.BookMetadataEntity;
import org.grimmory.model.entity.CategoryEntity;
import org.grimmory.model.entity.MoodEntity;
import org.grimmory.model.entity.TagEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class MetadataChangeDetectorTest {

    private BookMetadataEntity existingMeta;
    private BookMetadata newMeta;
    private MetadataClearFlags clearFlags;

    @BeforeEach
    void setup() {
        existingMeta = BookMetadataEntity.builder()
                .bookId(1L)
                .title("Original Title")
                .subtitle("Original Subtitle")
                .publisher("Original Publisher")
                .publishedDate(LocalDate.of(2020, 1, 1))
                .description("Original Description")
                .seriesName("Original Series")
                .seriesNumber(1.0f)
                .seriesTotal(5)
                .isbn13("9781234567890")
                .isbn10("1234567890")
                .asin("B012345678")
                .goodreadsId("12345678")
                .comicvineId("987654")
                .hardcoverId("hc123456")
                .googleId("google123")
                .pageCount(300)
                .language("en")
                .amazonRating(4.2)
                .amazonReviewCount(1500)
                .goodreadsRating(4.1)
                .goodreadsReviewCount(25000)
                .hardcoverRating(4.0)
                .hardcoverReviewCount(500)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .publishedDateLocked(false)
                .descriptionLocked(false)
                .seriesNameLocked(false)
                .seriesNumberLocked(false)
                .seriesTotalLocked(false)
                .isbn13Locked(false)
                .isbn10Locked(false)
                .asinLocked(false)
                .goodreadsIdLocked(false)
                .comicvineIdLocked(false)
                .hardcoverIdLocked(false)
                .hardcoverBookIdLocked(false)
                .googleIdLocked(false)
                .pageCountLocked(false)
                .languageLocked(false)
                .amazonRatingLocked(false)
                .amazonReviewCountLocked(false)
                .goodreadsRatingLocked(false)
                .goodreadsReviewCountLocked(false)
                .hardcoverRatingLocked(false)
                .hardcoverReviewCountLocked(false)
                .coverLocked(false)
                .authorsLocked(false)
                .categoriesLocked(false)
                .moodsLocked(false)
                .tagsLocked(false)
                .reviewsLocked(false)
                .authors(List.of(
                        AuthorEntity.builder().id(1L).name("Author One").build(),
                        AuthorEntity.builder().id(2L).name("Author Two").build()
                ))
                .categories(Set.of(
                        CategoryEntity.builder().id(1L).name("Fiction").build(),
                        CategoryEntity.builder().id(2L).name("Mystery").build()
                ))
                .moods(Set.of(
                        MoodEntity.builder().id(1L).name("Dark").build(),
                        MoodEntity.builder().id(2L).name("Suspenseful").build()
                ))
                .tags(Set.of(
                        TagEntity.builder().id(1L).name("Thriller").build(),
                        TagEntity.builder().id(2L).name("Bestseller").build()
                ))
                .build();

        newMeta = BookMetadata.builder()
                .bookId(1L)
                .title("Original Title")
                .subtitle("Original Subtitle")
                .publisher("Original Publisher")
                .publishedDate(LocalDate.of(2020, 1, 1))
                .description("Original Description")
                .seriesName("Original Series")
                .seriesNumber(1.0f)
                .seriesTotal(5)
                .isbn13("9781234567890")
                .isbn10("1234567890")
                .asin("B012345678")
                .goodreadsId("12345678")
                .comicvineId("987654")
                .hardcoverId("hc123456")
                .googleId("google123")
                .pageCount(300)
                .language("en")
                .amazonRating(4.2)
                .amazonReviewCount(1500)
                .goodreadsRating(4.1)
                .goodreadsReviewCount(25000)
                .hardcoverRating(4.0)
                .hardcoverReviewCount(500)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .publishedDateLocked(false)
                .descriptionLocked(false)
                .seriesNameLocked(false)
                .seriesNumberLocked(false)
                .seriesTotalLocked(false)
                .isbn13Locked(false)
                .isbn10Locked(false)
                .asinLocked(false)
                .goodreadsIdLocked(false)
                .comicvineIdLocked(false)
                .hardcoverIdLocked(false)
                .hardcoverBookIdLocked(false)
                .googleIdLocked(false)
                .pageCountLocked(false)
                .languageLocked(false)
                .amazonRatingLocked(false)
                .amazonReviewCountLocked(false)
                .goodreadsRatingLocked(false)
                .goodreadsReviewCountLocked(false)
                .hardcoverRatingLocked(false)
                .hardcoverReviewCountLocked(false)
                .coverLocked(false)
                .authorsLocked(false)
                .categoriesLocked(false)
                .moodsLocked(false)
                .tagsLocked(false)
                .reviewsLocked(false)
                .authors(List.of("Author One", "Author Two"))
                .categories(Set.of("Fiction", "Mystery"))
                .moods(Set.of("Dark", "Suspenseful"))
                .tags(Set.of("Thriller", "Bestseller"))
                .build();

        clearFlags = new MetadataClearFlags();
    }

    @Test
    void testIsDifferent_whenNoChanges_returnsFalse() {
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when no changes exist");
    }

    @ParameterizedTest(name = "{0} field change")
    @MethodSource("fieldChangeProvider")
    @DisplayName("isDifferent() detects changes in all metadata fields")
    void testIsDifferent_whenFieldChanges_returnsTrue(String fieldName, Consumer<BookMetadata> modifier) {
        modifier.accept(newMeta);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should detect change in " + fieldName);
    }

    static Stream<Arguments> fieldChangeProvider() {
        return Stream.of(
            Arguments.of("title", (Consumer<BookMetadata>) m -> m.setTitle("New Title")),
            Arguments.of("subtitle", (Consumer<BookMetadata>) m -> m.setSubtitle("New Subtitle")),
            Arguments.of("publisher", (Consumer<BookMetadata>) m -> m.setPublisher("New Publisher")),
            Arguments.of("publishedDate", (Consumer<BookMetadata>) m -> m.setPublishedDate(LocalDate.of(2021, 6, 15))),
            Arguments.of("description", (Consumer<BookMetadata>) m -> m.setDescription("New Description")),
            Arguments.of("seriesName", (Consumer<BookMetadata>) m -> m.setSeriesName("New Series")),
            Arguments.of("seriesNumber", (Consumer<BookMetadata>) m -> m.setSeriesNumber(2.0f)),
            Arguments.of("seriesTotal", (Consumer<BookMetadata>) m -> m.setSeriesTotal(10)),
            Arguments.of("isbn13", (Consumer<BookMetadata>) m -> m.setIsbn13("9780987654321")),
            Arguments.of("isbn10", (Consumer<BookMetadata>) m -> m.setIsbn10("0987654321")),
            Arguments.of("asin", (Consumer<BookMetadata>) m -> m.setAsin("B098765432")),
            Arguments.of("goodreadsId", (Consumer<BookMetadata>) m -> m.setGoodreadsId("87654321")),
            Arguments.of("comicvineId", (Consumer<BookMetadata>) m -> m.setComicvineId("123456")),
            Arguments.of("hardcoverId", (Consumer<BookMetadata>) m -> m.setHardcoverId("hc654321")),
            Arguments.of("googleId", (Consumer<BookMetadata>) m -> m.setGoogleId("google456")),
            Arguments.of("pageCount", (Consumer<BookMetadata>) m -> m.setPageCount(350)),
            Arguments.of("language", (Consumer<BookMetadata>) m -> m.setLanguage("fr")),
            Arguments.of("amazonRating", (Consumer<BookMetadata>) m -> m.setAmazonRating(4.5)),
            Arguments.of("amazonReviewCount", (Consumer<BookMetadata>) m -> m.setAmazonReviewCount(2000)),
            Arguments.of("goodreadsRating", (Consumer<BookMetadata>) m -> m.setGoodreadsRating(4.3)),
            Arguments.of("goodreadsReviewCount", (Consumer<BookMetadata>) m -> m.setGoodreadsReviewCount(30000)),
            Arguments.of("hardcoverRating", (Consumer<BookMetadata>) m -> m.setHardcoverRating(4.2)),
            Arguments.of("hardcoverReviewCount", (Consumer<BookMetadata>) m -> m.setHardcoverReviewCount(750)),
            Arguments.of("audibleId", (Consumer<BookMetadata>) m -> m.setAudibleId("B098765432")),
            Arguments.of("audibleRating", (Consumer<BookMetadata>) m -> m.setAudibleRating(4.6)),
            Arguments.of("audibleReviewCount", (Consumer<BookMetadata>) m -> m.setAudibleReviewCount(3000))
        );
    }

    @Test
    void testIsDifferent_whenNullClearFlags_returnsTrue() {
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, null);
        assertTrue(result, "Should return true when clear flags is null");
    }

    @Test
    void testIsDifferent_whenTitleLocked_changes_returnsTrue() {
        newMeta.setTitleLocked(true);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when title lock changes");
    }

    @Test
    void testIsDifferent_whenCoverLocked_changes_returnsTrue() {
        newMeta.setCoverLocked(true);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when cover lock changes");
    }

    @Test
    void testIsDifferent_whenFieldLocked_changes_returnsTrue() {
        existingMeta.setTitleLocked(true);
        newMeta.setTitleLocked(false);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when field lock changes");
    }

    @Test
    void testIsDifferent_whenFieldLocked_noChange_returnsFalse() {
        existingMeta.setTitleLocked(true);
        newMeta.setTitleLocked(true);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when field locks match");
    }

    @Test
    void testHasValueChanges_whenNoChanges_returnsFalse() {
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when no value changes exist");
    }

    @Test
    void testHasValueChanges_whenTitleChanged_returnsTrue() {
        newMeta.setTitle("New Title");
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when title value changes");
    }

    @Test
    void testHasValueChanges_whenLockedFieldChanged_returnsFalse() {
        existingMeta.setTitleLocked(true);
        newMeta.setTitle("New Title");
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when locked field changes");
    }

    @Test
    void testHasValueChanges_whenClearFlagSet_returnsTrue() {
        clearFlags.setTitle(true);
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when clear flag is set (even if values match)");
    }

    @Test
    void testHasValueChangesForFileWrite_whenNoChanges_returnsFalse() {
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when no value changes exist for file write");
    }

    @Test
    void testHasValueChangesForFileWrite_whenTitleChanged_returnsTrue() {
        newMeta.setTitle("New Title");
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when title value changes for file write");
    }

    @Test
    void testHasValueChangesForFileWrite_includesRatingsForFileWrite() {
        newMeta.setAmazonRating(4.8);
        newMeta.setGoodreadsRating(4.4);
        newMeta.setHardcoverRating(4.3);
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when rating fields change for file write");
    }

    @Test
    void testEdgeCase_nullToEmptyString_returnsFalse() {
        existingMeta.setTitle(null);
        newMeta.setTitle("");
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false for null to empty string transition");
    }

    @Test
    void testEdgeCase_nullToEmptyCollection_returnsFalse() {
        BookMetadataEntity testExisting = BookMetadataEntity.builder()
                .bookId(1L)
                .title("Original Title")
                .subtitle("Original Subtitle")
                .publisher("Original Publisher")
                .authors(null) // null authors, this is what we're testing
                .authorsLocked(false)
                .categories(Set.of(CategoryEntity.builder().id(1L).name("Fiction").build()))
                .categoriesLocked(false)
                .moods(Set.of(MoodEntity.builder().id(1L).name("Dark").build()))
                .moodsLocked(false)
                .tags(Set.of(TagEntity.builder().id(1L).name("Thriller").build()))
                .tagsLocked(false)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .build();

        BookMetadata testNew = BookMetadata.builder()
                .bookId(1L)
                .title("Original Title")
                .subtitle("Original Subtitle")
                .publisher("Original Publisher")
                .authors(List.of()) // empty set, this is what we're testing
                .authorsLocked(false)
                .categories(Set.of("Fiction"))
                .categoriesLocked(false)
                .moods(Set.of("Dark"))
                .moodsLocked(false)
                .tags(Set.of("Thriller"))
                .tagsLocked(false)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .build();

        boolean result = MetadataChangeDetector.isDifferent(testNew, testExisting, clearFlags);
        assertFalse(result, "Should return false because toNameSet converts null to empty set, so null → empty collection is no change");
    }

    @Test
    void testEdgeCase_emptyStringToNull_returnsTrue() {
        existingMeta.setTitle("");
        newMeta.setTitle(null);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true for empty string to null transition");
    }

    @Test
    void testEdgeCase_emptyCollectionToNull_returnsTrue() {
        BookMetadataEntity testExisting = BookMetadataEntity.builder()
                .bookId(1L)
                .title("Test Title")
                .authors(List.of()) // empty set, what we're testing
                .authorsLocked(false)
                .categories(Set.of(CategoryEntity.builder().id(1L).name("Fiction").build()))
                .categoriesLocked(false)
                .moods(Set.of(MoodEntity.builder().id(1L).name("Dark").build()))
                .moodsLocked(false)
                .tags(Set.of(TagEntity.builder().id(1L).name("Tag").build()))
                .tagsLocked(false)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .publishedDateLocked(false)
                .descriptionLocked(false)
                .seriesNameLocked(false)
                .seriesNumberLocked(false)
                .seriesTotalLocked(false)
                .isbn13Locked(false)
                .isbn10Locked(false)
                .asinLocked(false)
                .goodreadsIdLocked(false)
                .comicvineIdLocked(false)
                .hardcoverIdLocked(false)
                .hardcoverBookIdLocked(false)
                .googleIdLocked(false)
                .pageCountLocked(false)
                .languageLocked(false)
                .amazonRatingLocked(false)
                .amazonReviewCountLocked(false)
                .goodreadsRatingLocked(false)
                .goodreadsReviewCountLocked(false)
                .hardcoverRatingLocked(false)
                .hardcoverReviewCountLocked(false)
                .coverLocked(false)
                .reviewsLocked(false)
                .build();

        BookMetadata testNew = BookMetadata.builder()
                .bookId(1L)
                .title("Test Title")
                .authors(null) // null, what we're testing
                .authorsLocked(false)
                .categories(Set.of("Fiction"))  // Match existing
                .categoriesLocked(false)
                .moods(Set.of("Dark"))          // Match existing
                .moodsLocked(false)
                .tags(Set.of("Tag"))            // Match existing
                .tagsLocked(false)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .publishedDateLocked(false)
                .descriptionLocked(false)
                .seriesNameLocked(false)
                .seriesNumberLocked(false)
                .seriesTotalLocked(false)
                .isbn13Locked(false)
                .isbn10Locked(false)
                .asinLocked(false)
                .goodreadsIdLocked(false)
                .comicvineIdLocked(false)
                .hardcoverIdLocked(false)
                .hardcoverBookIdLocked(false)
                .googleIdLocked(false)
                .pageCountLocked(false)
                .languageLocked(false)
                .amazonRatingLocked(false)
                .amazonReviewCountLocked(false)
                .goodreadsRatingLocked(false)
                .goodreadsReviewCountLocked(false)
                .hardcoverRatingLocked(false)
                .hardcoverReviewCountLocked(false)
                .coverLocked(false)
                .reviewsLocked(false)
                .build();
        boolean result = MetadataChangeDetector.isDifferent(testNew, testExisting, clearFlags);
        assertTrue(result, "Should return true for empty collection to null transition");
    }

    @Test
    @DisplayName("Locked field blocks value changes but still detects lock state changes")
    void testIsDifferent_whenFieldLockedAndValueDifferent_stillDetectsLockChange() {
        existingMeta.setTitleLocked(true);
        newMeta.setTitle("New Title"); // value change (should be ignored due to lock)
        newMeta.setTitleLocked(false); // lock change (should be detected)

        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should detect lock change even if value change is blocked by lock");

        boolean valueResult = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(valueResult, "Should not detect value change when field is locked");
    }

    @Test
    void testIsDifferent_whenFieldLockedAndOnlyValueDifferent_returnsFalse() {
        existingMeta.setTitleLocked(true);
        newMeta.setTitle("New Title");
        newMeta.setTitleLocked(true); // lock unchanged

        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should not detect value change when field is locked and lock is unchanged");
    }

    @Test
    void testClearFlag_whenFieldUnlocked_triggersChange() {
        BookMetadataEntity testExisting = BookMetadataEntity.builder()
                .bookId(1L)
                .title("Existing Title")
                .titleLocked(false) // field is unlocked
                .build();
        BookMetadata testNew = BookMetadata.builder()
                .bookId(1L)
                .title("Existing Title") // same title
                .titleLocked(false)
                .build();
        MetadataClearFlags testClearFlags = new MetadataClearFlags();
        testClearFlags.setTitle(true); // clear flag forces change detection

        boolean result = MetadataChangeDetector.hasValueChanges(testNew, testExisting, testClearFlags);
        assertTrue(result, "Clear flag should trigger change when field is unlocked, even with identical values");
    }

    @Test
    @DisplayName("Lock takes precedence over clear flag")
    void testHasValueChanges_whenClearFlagTrue_lockStillTakesPrecedence() {
        existingMeta.setTitleLocked(true);
        newMeta.setTitle("New Title");
        clearFlags.setTitle(true);

        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Lock should take precedence over clear flag");
    }

    @Test
    void testClearFlag_whenExistingValueIsNull_returnsFalse() {
        existingMeta.setTitle(null);
        existingMeta.setTitleLocked(false);
        newMeta.setTitle("New Title");
        clearFlags.setTitle(true);

        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Clear flag with null existing value means nothing to clear");
    }

    @Test
    void testAuthorsComparison_isOrderSensitive() {
        newMeta.setAuthors(List.of("Author Two", "Author One"));

        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Authors comparison should be order sensitive");
    }

    @Test
    void testIsDifferent_whenClearFlagTrue_andValuesIdentical_returnsTrue() {
        // newMeta and existingMeta have identical title values
        clearFlags.setTitle(true); // but clear flag forces change detection

        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "isDifferent should return true when clear flag is set, even if values are identical");
    }

    @Test
    @DisplayName("hasValueChangesForFileWrite() includes booklore-namespace fields that are written to files")
    void testHasValueChangesForFileWrite_includesPageCount() {
        newMeta.setPageCount(999);
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "PageCount should trigger file write");
    }

    @Test
    void testHasValueChangesForFileWrite_includesMoods() {
        newMeta.setMoods(Set.of("New Mood"));
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Moods should trigger file write");
    }

    @Test
    void testHasValueChangesForFileWrite_includesTags() {
        newMeta.setTags(Set.of("New Tag"));
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Tags should trigger file write");
    }

    @Test
    void testHasValueChangesForFileWrite_includesRatingFields() {
        newMeta.setAmazonRating(4.9);
        newMeta.setGoodreadsRating(4.8);
        newMeta.setHardcoverRating(4.7);
        newMeta.setAmazonReviewCount(2000);
        newMeta.setGoodreadsReviewCount(35000);
        newMeta.setHardcoverReviewCount(800);
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Rating and review count fields should trigger file write");
    }

    @Test
    void testHasValueChangesForFileWrite_excludesAudiobookFields() {
        newMeta.setAudibleRating(4.6);
        newMeta.setAudibleReviewCount(3000);
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Audiobook-only fields should not trigger file write");
    }

    @Test
    void testHasValueChangesForFileWrite_includesAuthors() {
        newMeta.setAuthors(List.of("New Author"));
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Authors change should trigger file write");
    }

    @Test
    void testHasValueChangesForFileWrite_includesCategories() {
        newMeta.setCategories(Set.of("New Category"));
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Categories change should trigger file write");
    }

    @Test
    void testIsDifferent_whenBothSubtitleValuesNull_returnsFalse() {
        existingMeta.setSubtitle(null);
        newMeta.setSubtitle(null);

        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when both values are null");
    }

    @Test
    void testIsDifferent_whenMultipleFieldsChange_returnsTrue() {
        newMeta.setTitle("New Title");
        newMeta.setSubtitle("New Subtitle");
        newMeta.setPublisher("New Publisher");
        newMeta.setPageCount(999);

        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when multiple fields change simultaneously");
    }

    @Test
    void testEdgeCase_whitespaceNormalization() {
        existingMeta.setTitle("  Original Title  ");
        newMeta.setTitle("Original Title");
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when strings are equal after whitespace normalization");
    }

    @Test
    void testIsDifferent_whenAuthorsOrderChanges_returnsTrue() {
        newMeta.setAuthors(List.of("Author Two", "Author One"));
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when author order changes");
    }

    @Test
    void testIsDifferent_whenAuthorsSetContentChanges_returnsTrue() {
        newMeta.setAuthors(List.of("Author One", "Author Three")); // Different author
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when author set content changes");
    }

    @Test
    void testIsDifferent_whenCategoriesSetChanges_returnsTrue() {
        newMeta.setCategories(Set.of("Fiction", "New Category"));
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when categories change");
    }

    @Test
    void testIsDifferent_whenMoodsSetChanges_returnsTrue() {
        newMeta.setMoods(Set.of("Dark", "New Mood"));
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when moods change");
    }

    @Test
    void testIsDifferent_whenTagsSetChanges_returnsTrue() {
        newMeta.setTags(Set.of("Thriller", "New Tag"));
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when tags change");
    }

    @Test
    void testHasValueChanges_whenAuthorsOrderChanges_returnsTrue() {
        newMeta.setAuthors(List.of("Author Two", "Author One"));
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when author order changes");
    }

    @Test
    void testHasValueChanges_whenMultipleCollectionsChange_returnsTrue() {
        newMeta.setAuthors(List.of("New Author"));
        newMeta.setCategories(Set.of("New Category"));
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when multiple collections change");
    }

    @Test
    void testHasValueChanges_whenEmptyStringToNull_returnsTrue() {
        existingMeta.setTitle("");
        newMeta.setTitle(null);
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true for empty string to null transition");
    }

    @Test
    void testHasValueChanges_whenNullToEmptyString_returnsFalse() {
        existingMeta.setTitle(null);
        newMeta.setTitle("");
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false for null to empty string transition");
    }

    @Test
    void testHasValueChanges_whenEmptySetToNull_returnsTrue() {
        BookMetadataEntity testExisting = BookMetadataEntity.builder()
                .bookId(1L)
                .title("Test")
                .authors(List.of())
                .authorsLocked(false)
                .categories(Set.of())
                .categoriesLocked(false)
                .moods(Set.of())
                .moodsLocked(false)
                .tags(Set.of())
                .tagsLocked(false)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .publishedDateLocked(false)
                .descriptionLocked(false)
                .seriesNameLocked(false)
                .seriesNumberLocked(false)
                .seriesTotalLocked(false)
                .isbn13Locked(false)
                .isbn10Locked(false)
                .asinLocked(false)
                .goodreadsIdLocked(false)
                .comicvineIdLocked(false)
                .hardcoverIdLocked(false)
                .hardcoverBookIdLocked(false)
                .googleIdLocked(false)
                .pageCountLocked(false)
                .languageLocked(false)
                .amazonRatingLocked(false)
                .amazonReviewCountLocked(false)
                .goodreadsRatingLocked(false)
                .goodreadsReviewCountLocked(false)
                .hardcoverRatingLocked(false)
                .hardcoverReviewCountLocked(false)
                .coverLocked(false)
                .reviewsLocked(false)
                .build();
        BookMetadata testNew = BookMetadata.builder()
                .bookId(1L)
                .title("Test")
                .authors(null)
                .authorsLocked(false)
                .categories(Set.of())
                .categoriesLocked(false)
                .moods(Set.of())
                .moodsLocked(false)
                .tags(Set.of())
                .tagsLocked(false)
                .titleLocked(false)
                .subtitleLocked(false)
                .publisherLocked(false)
                .publishedDateLocked(false)
                .descriptionLocked(false)
                .seriesNameLocked(false)
                .seriesNumberLocked(false)
                .seriesTotalLocked(false)
                .isbn13Locked(false)
                .isbn10Locked(false)
                .asinLocked(false)
                .goodreadsIdLocked(false)
                .comicvineIdLocked(false)
                .hardcoverIdLocked(false)
                .hardcoverBookIdLocked(false)
                .googleIdLocked(false)
                .pageCountLocked(false)
                .languageLocked(false)
                .amazonRatingLocked(false)
                .amazonReviewCountLocked(false)
                .goodreadsRatingLocked(false)
                .goodreadsReviewCountLocked(false)
                .hardcoverRatingLocked(false)
                .hardcoverReviewCountLocked(false)
                .coverLocked(false)
                .reviewsLocked(false)
                .build();
        boolean result = MetadataChangeDetector.hasValueChanges(testNew, testExisting, clearFlags);
        assertTrue(result, "Should return true for empty set to null transition");
    }

    @Test
    void testHasValueChangesForFileWrite_whenTitleAndPageCountChange_onlyTitleTriggers() {
        newMeta.setTitle("New Title");
        newMeta.setPageCount(999);
        boolean result = MetadataChangeDetector.hasValueChangesForFileWrite(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when title changes, even if pageCount also changes");
    }

    @Test
    void testIsDifferent_whenOnlyLockChanges_noValueChange_returnsTrue() {
        newMeta.setTitleLocked(true);
        // Title value is same, only lock changes
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when only lock changes, even if value is same");
    }

    @Test
    void testIsDifferent_whenCoverLockChanges_returnsTrue() {
        newMeta.setCoverLocked(true);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when cover lock changes");
    }

    @Test
    void testIsDifferent_whenCoverLockUnchanged_returnsFalse() {
        existingMeta.setCoverLocked(true);
        newMeta.setCoverLocked(true);
        boolean result = MetadataChangeDetector.isDifferent(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when cover lock unchanged");
    }

    @Test
    void testHasValueChanges_whenStringWithWhitespaceChanges_returnsTrue() {
        existingMeta.setTitle("Original Title");
        newMeta.setTitle("  New Title  ");
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when normalized strings differ");
    }

    @Test
    void testHasValueChanges_whenStringWhitespaceOnly_returnsFalse() {
        existingMeta.setTitle("Original Title");
        newMeta.setTitle("  Original Title  "); // Same after normalization
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when strings are same after normalization");
    }

    @Test
    void testHasValueChanges_whenFloatSeriesNumberChanges_returnsTrue() {
        newMeta.setSeriesNumber(2.5f);
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertTrue(result, "Should return true when series number changes");
    }

    @Test
    void testHasValueChanges_whenFloatSeriesNumberUnchanged_returnsFalse() {
        boolean result = MetadataChangeDetector.hasValueChanges(newMeta, existingMeta, clearFlags);
        assertFalse(result, "Should return false when series number unchanged");
    }
}
