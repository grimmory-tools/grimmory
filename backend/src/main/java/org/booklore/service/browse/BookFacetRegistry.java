package org.booklore.service.browse;

import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class BookFacetRegistry {

    private static final String MAGIC_SHELF_PREFIX = "magic:";

    private static final Set<String> NAMES = Set.of(
            "author", "series", "genre", "tag", "mood", "language", "publisher", "narrator", "library", "shelf",
            "file_type", "read_status", "personal_rating", "amazon_rating", "goodreads_rating",
            "hardcover_rating", "ranobedb_rating", "lubimyczytac_rating", "audible_rating", "applebooks_rating",
            "age_rating", "content_rating", "match_score",
            "published_year", "file_size", "page_count", "shelf_status",
            "comic_character", "comic_team", "comic_location", "comic_creator");

    private final MagicShelfBookService magicShelfBookService;

    public BookFacetRegistry(MagicShelfBookService magicShelfBookService) {
        this.magicShelfBookService = magicShelfBookService;
    }

    public boolean has(String facetName) {
        return NAMES.contains(facetName);
    }

    public Set<String> facetNames() {
        return NAMES;
    }

    public Specification<BookEntity> matching(String facetName, List<String> values, Long userId) {
        return switch (facetName) {
            case "author" -> BookFilterSpecification.withAuthors(values);
            case "series" -> BookFilterSpecification.inSeriesMulti(values);
            case "genre" -> BookFilterSpecification.withCategories(values);
            case "tag" -> BookFilterSpecification.withTags(values);
            case "mood" -> BookFilterSpecification.withMoods(values);
            case "language" -> BookFilterSpecification.withLanguages(values);
            case "publisher" -> BookFilterSpecification.withPublishers(values);
            case "narrator" -> BookFilterSpecification.withNarrators(values);
            case "library" -> BookFilterSpecification.inLibraries(values);
            case "shelf" -> shelves(values, userId);
            case "file_type" -> BookFilterSpecification.withFileTypes(values);
            case "read_status" -> BookFilterSpecification.withReadStatuses(values, userId);
            case "personal_rating" -> BookFilterSpecification.withPersonalRatings(values, userId);
            case "amazon_rating" -> BookFilterSpecification.withAmazonRatings(values);
            case "goodreads_rating" -> BookFilterSpecification.withGoodreadsRatings(values);
            case "hardcover_rating" -> BookFilterSpecification.withHardcoverRatings(values);
            case "ranobedb_rating" -> BookFilterSpecification.withRanobedbRatings(values);
            case "lubimyczytac_rating" -> BookFilterSpecification.withLubimyczytacRatings(values);
            case "audible_rating" -> BookFilterSpecification.withAudibleRatings(values);
            case "applebooks_rating" -> BookFilterSpecification.withApplebooksRatings(values);
            case "age_rating" -> BookFilterSpecification.withAgeRatings(values);
            case "content_rating" -> BookFilterSpecification.withContentRatings(values);
            case "match_score" -> BookFilterSpecification.withMatchScores(values);
            case "published_year" -> BookFilterSpecification.withPublishedYears(values);
            case "file_size" -> BookFilterSpecification.withFileSizes(values);
            case "page_count" -> BookFilterSpecification.withPageCounts(values);
            case "shelf_status" -> BookFilterSpecification.withShelfStatus(values);
            case "comic_character" -> BookFilterSpecification.withComicCharacters(values);
            case "comic_team" -> BookFilterSpecification.withComicTeams(values);
            case "comic_location" -> BookFilterSpecification.withComicLocations(values);
            case "comic_creator" -> BookFilterSpecification.withComicCreators(values);
            default -> throw ApiError.INVALID_FACET.createException("Unknown facet: " + facetName);
        };
    }

    private Specification<BookEntity> shelves(List<String> values, Long userId) {
        List<String> regularIds = new ArrayList<>();
        List<Specification<BookEntity>> specs = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (value.startsWith(MAGIC_SHELF_PREFIX)) {
                specs.add(magicShelfBookService.toSpecification(userId, parseMagicShelfId(value)));
            } else {
                regularIds.add(value);
            }
        }
        if (!regularIds.isEmpty()) {
            specs.add(BookFilterSpecification.inShelves(regularIds));
        }
        if (specs.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return Specification.anyOf(specs);
    }

    private static long parseMagicShelfId(String value) {
        try {
            return Long.parseLong(value.substring(MAGIC_SHELF_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw ApiError.INVALID_FACET.createException("Invalid magic shelf id: " + value);
        }
    }
}
