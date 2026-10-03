package org.booklore.service.browse;

import org.booklore.app.specification.AppBookSpecification;
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
            case "author" -> AppBookSpecification.withAuthors(values, "or");
            case "series" -> AppBookSpecification.inSeriesMulti(values, "or");
            case "genre" -> AppBookSpecification.withCategories(values, "or");
            case "tag" -> AppBookSpecification.withTags(values, "or");
            case "mood" -> AppBookSpecification.withMoods(values, "or");
            case "language" -> AppBookSpecification.withLanguages(values, "or");
            case "publisher" -> AppBookSpecification.withPublishers(values, "or");
            case "narrator" -> AppBookSpecification.withNarrators(values, "or");
            case "library" -> AppBookSpecification.inLibraries(values, "or");
            case "shelf" -> shelves(values, userId);
            case "file_type" -> AppBookSpecification.withFileTypes(values, "or");
            case "read_status" -> AppBookSpecification.withReadStatuses(values, userId, "or");
            case "personal_rating" -> AppBookSpecification.withPersonalRatings(values, userId, "or");
            case "amazon_rating" -> AppBookSpecification.withAmazonRatings(values, "or");
            case "goodreads_rating" -> AppBookSpecification.withGoodreadsRatings(values, "or");
            case "hardcover_rating" -> AppBookSpecification.withHardcoverRatings(values, "or");
            case "ranobedb_rating" -> AppBookSpecification.withRanobedbRatings(values, "or");
            case "lubimyczytac_rating" -> AppBookSpecification.withLubimyczytacRatings(values, "or");
            case "audible_rating" -> AppBookSpecification.withAudibleRatings(values, "or");
            case "applebooks_rating" -> AppBookSpecification.withApplebooksRatings(values, "or");
            case "age_rating" -> AppBookSpecification.withAgeRatings(values, "or");
            case "content_rating" -> AppBookSpecification.withContentRatings(values, "or");
            case "match_score" -> AppBookSpecification.withMatchScores(values, "or");
            case "published_year" -> AppBookSpecification.withPublishedYears(values, "or");
            case "file_size" -> AppBookSpecification.withFileSizes(values, "or");
            case "page_count" -> AppBookSpecification.withPageCounts(values, "or");
            case "shelf_status" -> AppBookSpecification.withShelfStatus(values, "or");
            case "comic_character" -> AppBookSpecification.withComicCharacters(values, "or");
            case "comic_team" -> AppBookSpecification.withComicTeams(values, "or");
            case "comic_location" -> AppBookSpecification.withComicLocations(values, "or");
            case "comic_creator" -> AppBookSpecification.withComicCreators(values, "or");
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
            specs.add(AppBookSpecification.inShelves(regularIds, "or"));
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
