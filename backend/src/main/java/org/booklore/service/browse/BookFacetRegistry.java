package org.booklore.service.browse;

import org.booklore.browse.FacetLogic;
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

    public Specification<BookEntity> toSpecification(String facetName, List<String> values, FacetLogic logic, Long userId) {
        String mode = mode(logic);
        return switch (facetName) {
            case "author" -> BookSpecification.withAuthors(values, mode);
            case "series" -> BookSpecification.inSeriesMulti(values, mode);
            case "genre" -> BookSpecification.withCategories(values, mode);
            case "tag" -> BookSpecification.withTags(values, mode);
            case "mood" -> BookSpecification.withMoods(values, mode);
            case "language" -> BookSpecification.withLanguages(values, mode);
            case "publisher" -> BookSpecification.withPublishers(values, mode);
            case "narrator" -> BookSpecification.withNarrators(values, mode);
            case "library" -> BookSpecification.inLibraries(values, mode);
            case "shelf" -> shelves(values, logic, userId);
            case "file_type" -> BookSpecification.withFileTypes(values, mode);
            case "read_status" -> BookSpecification.withReadStatuses(values, userId, mode);
            case "personal_rating" -> BookSpecification.withPersonalRatings(values, userId, mode);
            case "amazon_rating" -> BookSpecification.withAmazonRatings(values, mode);
            case "goodreads_rating" -> BookSpecification.withGoodreadsRatings(values, mode);
            case "hardcover_rating" -> BookSpecification.withHardcoverRatings(values, mode);
            case "ranobedb_rating" -> BookSpecification.withRanobedbRatings(values, mode);
            case "lubimyczytac_rating" -> BookSpecification.withLubimyczytacRatings(values, mode);
            case "audible_rating" -> BookSpecification.withAudibleRatings(values, mode);
            case "applebooks_rating" -> BookSpecification.withApplebooksRatings(values, mode);
            case "age_rating" -> BookSpecification.withAgeRatings(values, mode);
            case "content_rating" -> BookSpecification.withContentRatings(values, mode);
            case "match_score" -> BookSpecification.withMatchScores(values, mode);
            case "published_year" -> BookSpecification.withPublishedYears(values, mode);
            case "file_size" -> BookSpecification.withFileSizes(values, mode);
            case "page_count" -> BookSpecification.withPageCounts(values, mode);
            case "shelf_status" -> BookSpecification.withShelfStatus(values, mode);
            case "comic_character" -> BookSpecification.withComicCharacters(values, mode);
            case "comic_team" -> BookSpecification.withComicTeams(values, mode);
            case "comic_location" -> BookSpecification.withComicLocations(values, mode);
            case "comic_creator" -> BookSpecification.withComicCreators(values, mode);
            default -> throw ApiError.INVALID_FACET.createException("Unknown facet: " + facetName);
        };
    }

    private Specification<BookEntity> shelves(List<String> values, FacetLogic logic, Long userId) {
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
            String inMode = logic == FacetLogic.AND ? "and" : "or";
            specs.add(BookSpecification.inShelves(regularIds, inMode));
        }
        if (specs.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        return switch (logic) {
            case OR -> Specification.anyOf(specs);
            case NOT -> Specification.not(Specification.anyOf(specs));
            case AND -> Specification.allOf(specs);
        };
    }

    private static long parseMagicShelfId(String value) {
        try {
            return Long.parseLong(value.substring(MAGIC_SHELF_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw ApiError.INVALID_FACET.createException("Invalid magic shelf id: " + value);
        }
    }

    private static String mode(FacetLogic logic) {
        return switch (logic) {
            case OR -> "or";
            case NOT -> "not";
            case AND -> "and";
        };
    }
}
