package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.booklore.browse.FacetLogic;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.ComicCharacterEntity;
import org.booklore.model.entity.ComicCreatorEntity;
import org.booklore.model.entity.ComicLocationEntity;
import org.booklore.model.entity.ComicTeamEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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
    @PersistenceContext
    private EntityManager entityManager;

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
            case "author" -> namedFilter(AuthorEntity.class, "authors", values, mode);
            case "genre" -> namedFilter(CategoryEntity.class, "categories", values, mode);
            case "tag" -> namedFilter(TagEntity.class, "tags", values, mode);
            case "mood" -> namedFilter(MoodEntity.class, "moods", values, mode);
            case "comic_character" -> BookFilterSpecification.withComicCollection("characters", values, mode,
                    idsFor(ComicCharacterEntity.class));
            case "comic_team" -> BookFilterSpecification.withComicCollection("teams", values, mode,
                    idsFor(ComicTeamEntity.class));
            case "comic_location" -> BookFilterSpecification.withComicCollection("locations", values, mode,
                    idsFor(ComicLocationEntity.class));
            case "comic_creator" -> BookFilterSpecification.withComicCreators(values, mode, idsFor(ComicCreatorEntity.class));
            case "series" -> BookFilterSpecification.inSeriesMulti(values, mode);
            case "language" -> BookFilterSpecification.withLanguages(values, mode);
            case "publisher" -> BookFilterSpecification.withPublishers(values, mode);
            case "narrator" -> BookFilterSpecification.withNarrators(values, mode);
            case "library" -> BookFilterSpecification.inLibraries(values, mode);
            case "shelf" -> shelves(values, logic, userId);
            case "file_type" -> BookFilterSpecification.withFileTypes(values, mode);
            case "read_status" -> BookFilterSpecification.withReadStatuses(values, userId, mode);
            case "personal_rating" -> BookFilterSpecification.withPersonalRatings(values, userId, mode);
            case "amazon_rating" -> BookFilterSpecification.withAmazonRatings(values, mode);
            case "goodreads_rating" -> BookFilterSpecification.withGoodreadsRatings(values, mode);
            case "hardcover_rating" -> BookFilterSpecification.withHardcoverRatings(values, mode);
            case "ranobedb_rating" -> BookFilterSpecification.withRanobedbRatings(values, mode);
            case "lubimyczytac_rating" -> BookFilterSpecification.withLubimyczytacRatings(values, mode);
            case "audible_rating" -> BookFilterSpecification.withAudibleRatings(values, mode);
            case "applebooks_rating" -> BookFilterSpecification.withApplebooksRatings(values, mode);
            case "age_rating" -> BookFilterSpecification.withAgeRatings(values, mode);
            case "content_rating" -> BookFilterSpecification.withContentRatings(values, mode);
            case "match_score" -> BookFilterSpecification.withMatchScores(values, mode);
            case "published_year" -> BookFilterSpecification.withPublishedYears(values, mode);
            case "file_size" -> BookFilterSpecification.withFileSizes(values, mode);
            case "page_count" -> BookFilterSpecification.withPageCounts(values, mode);
            case "shelf_status" -> BookFilterSpecification.withShelfStatus(values, mode);
            default -> throw ApiError.INVALID_FACET.createException("Unknown facet: " + facetName);
        };
    }

    private Specification<BookEntity> namedFilter(Class<?> type, String path, List<String> values, String mode) {
        return BookFilterSpecification.withMetadataCollection(path, values, mode, idsFor(type));
    }

    private Function<String, List<Long>> idsFor(Class<?> type) {
        return name -> matchingIds(type, name);
    }

    private List<Long> matchingIds(Class<?> type, String name) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Long.class);
        var root = query.from(type);
        query.select(root.get("id")).where(cb.equal(cb.lower(root.get("name")), name));
        return entityManager.createQuery(query).getResultList();
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
            specs.add(BookFilterSpecification.inShelves(regularIds, inMode));
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
