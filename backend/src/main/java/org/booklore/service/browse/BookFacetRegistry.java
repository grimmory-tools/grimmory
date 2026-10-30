package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    public Specification<BookEntity> matching(String facetName, List<String> values, Long userId) {
        return switch (facetName) {
            case "author" -> namedFilter(AuthorEntity.class, "authors", values);
            case "series" -> BookFilterSpecification.inSeriesMulti(values);
            case "genre" -> namedFilter(CategoryEntity.class, "categories", values);
            case "tag" -> namedFilter(TagEntity.class, "tags", values);
            case "mood" -> namedFilter(MoodEntity.class, "moods", values);
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
            case "comic_character" -> BookFilterSpecification.withComicCollection("characters", values, idsFor(ComicCharacterEntity.class));
            case "comic_team" -> BookFilterSpecification.withComicCollection("teams", values, idsFor(ComicTeamEntity.class));
            case "comic_location" -> BookFilterSpecification.withComicCollection("locations", values, idsFor(ComicLocationEntity.class));
            case "comic_creator" -> BookFilterSpecification.withComicCreators(values, idsFor(ComicCreatorEntity.class));
            default -> throw ApiError.INVALID_FACET.createException("Unknown facet: " + facetName);
        };
    }

    private Specification<BookEntity> namedFilter(Class<?> type, String path, List<String> values) {
        return BookFilterSpecification.withMetadataCollection(path, values, idsFor(type));
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
