package org.booklore.service.browse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.CategoryEntity;
import org.booklore.model.entity.ComicCharacterEntity;
import org.booklore.model.entity.ComicCreatorEntity;
import org.booklore.model.entity.ComicLocationEntity;
import org.booklore.model.entity.ComicTeamEntity;
import org.booklore.model.entity.MoodEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.TagEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.booklore.service.browse.FacetDef.names;
import static org.booklore.service.browse.FacetDef.number;
import static org.booklore.service.browse.FacetDef.values;

@Component
public class BookFacetRegistry {

    private static final String MAGIC_SHELF_PREFIX = "magic:";

    private static final List<String> RATING_BANDS = List.of("0..<1", "1..<2", "2..<3", "3..<4", "4..<4.5", "4.5..*");
    private static final List<String> MATCH_SCORE_BANDS = List.of("95..*", "90..<95", "80..<90", "70..<80", "50..<70", "30..<50", "0..<30");

    private final MagicShelfBookService magicShelfBookService;
    private final FacetRegistry<BookEntity> registry;

    @PersistenceContext
    private EntityManager entityManager;

    public BookFacetRegistry(MagicShelfBookService magicShelfBookService) {
        this.magicShelfBookService = magicShelfBookService;
        this.registry = build();
    }

    public FacetRegistry<BookEntity> registry() {
        return registry;
    }

    private FacetRegistry<BookEntity> build() {
        return new FacetRegistry<>(List.of(
                names("author", "Authors", any(values -> namedFilter(AuthorEntity.class, "authors", values)),
                        (root, query, cb, scope) -> metadata(root).join("authors", JoinType.LEFT).get("name")),
                names("genre", "Genre", any(values -> namedFilter(CategoryEntity.class, "categories", values)),
                        (root, query, cb, scope) -> metadata(root).join("categories", JoinType.LEFT).get("name")),
                names("tag", "Tags", any(values -> namedFilter(TagEntity.class, "tags", values)),
                        (root, query, cb, scope) -> metadata(root).join("tags", JoinType.LEFT).get("name")),
                names("mood", "Moods", any(values -> namedFilter(MoodEntity.class, "moods", values)),
                        (root, query, cb, scope) -> metadata(root).join("moods", JoinType.LEFT).get("name")),
                names("series", "Series", any(BookFilterSpecification::inSeriesMulti),
                        (root, query, cb, scope) -> metadata(root).get("seriesName")),
                names("publisher", "Publisher", any(BookFilterSpecification::withPublishers),
                        (root, query, cb, scope) -> metadata(root).get("publisher")),
                names("language", "Language", any(BookFilterSpecification::withLanguages),
                        (root, query, cb, scope) -> metadata(root).get("language")),
                names("narrator", "Narrator", any(BookFilterSpecification::withNarrators),
                        (root, query, cb, scope) -> metadata(root).get("narrator")),
                values("file_type", "File Type", any(BookFilterSpecification::withFileTypes),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, BookFileEntity> files = root.join("bookFiles", JoinType.LEFT);
                            files.on(cb.isTrue(files.get("isBookFormat")));
                            return files.get("bookType");
                        },
                        (root, query, cb, scope) -> cb.<String>selectCase()
                                .when(cb.isTrue(root.get("isPhysical")), "PHYSICAL")
                                .otherwise(cb.nullLiteral(String.class))),
                values("content_rating", "Content Rating", any(BookFilterSpecification::withContentRatings),
                        (root, query, cb, scope) -> metadata(root).get("contentRating")),
                rating("amazon_rating", "Amazon Rating", BookFilterSpecification::withAmazonRatings, "amazonRating"),
                rating("goodreads_rating", "Goodreads Rating", BookFilterSpecification::withGoodreadsRatings, "goodreadsRating"),
                rating("hardcover_rating", "Hardcover Rating", BookFilterSpecification::withHardcoverRatings, "hardcoverRating"),
                rating("ranobedb_rating", "RanobeDB Rating", BookFilterSpecification::withRanobedbRatings, "ranobedbRating"),
                rating("lubimyczytac_rating", "Lubimyczytac Rating", BookFilterSpecification::withLubimyczytacRatings, "lubimyczytacRating"),
                rating("audible_rating", "Audible Rating", BookFilterSpecification::withAudibleRatings, "audibleRating"),
                rating("applebooks_rating", "Apple Books Rating", BookFilterSpecification::withApplebooksRatings, "applebooksRating"),
                values("age_rating", "Age Rating", any(BookFilterSpecification::withAgeRatings),
                        (root, query, cb, scope) -> metadata(root).get("ageRating")),
                number("page_count", "Page Count", any(BookFilterSpecification::withPageCounts),
                        (root, query, cb, scope) -> metadata(root).get("pageCount")),
                number("match_score", "Match Score", any(BookFilterSpecification::withMatchScores),
                        (root, query, cb, scope) -> root.get("metadataMatchScore")).banded(MATCH_SCORE_BANDS),
                number("published_year", "Published Year", any(BookFilterSpecification::withPublishedYears),
                        (root, query, cb, scope) -> cb.function("YEAR", Integer.class, metadata(root).get("publishedDate"))),
                values("library", "Library", any(BookFilterSpecification::inLibraries),
                        (root, query, cb, scope) -> root.join("library").get("id")).uncapped(),
                values("shelf", "Shelf", this::shelves,
                        (root, query, cb, scope) -> {
                            Join<BookEntity, ShelfEntity> shelves = root.join("shelves", JoinType.LEFT);
                            shelves.on(BookFilterSpecification.visibleShelf(cb, shelves, scope.userId()));
                            return shelves.get("id");
                        }).uncapped().withExtraValues(this::magicShelves),
                values("shelf_status", "Shelf Status",
                        (values, scope) -> BookFilterSpecification.withShelfStatus(values, scope.userId()))
                        .withExtraValues(this::shelfStatuses),
                values("read_status", "Read Status",
                        (values, scope) -> BookFilterSpecification.withReadStatuses(values, scope.userId()),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, UserBookProgressEntity> progress = root.join("userBookProgress", JoinType.LEFT);
                            progress.on(cb.equal(progress.get("user").get("id"), scope.userId()));
                            return cb.<ReadStatus>selectCase()
                                    .when(progress.get("id").isNull(), ReadStatus.UNSET)
                                    .otherwise(progress.get("readStatus"));
                        }),
                values("personal_rating", "Personal Rating",
                        (values, scope) -> BookFilterSpecification.withPersonalRatings(values, scope.userId()),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, UserBookProgressEntity> progress = root.join("userBookProgress");
                            progress.on(cb.equal(progress.get("user").get("id"), scope.userId()));
                            return progress.get("personalRating");
                        }),
                number("file_size", "File Size", any(BookFilterSpecification::withFileSizes),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, BookFileEntity> files = root.join("bookFiles", JoinType.LEFT);
                            files.on(cb.isTrue(files.get("isBookFormat")));
                            return files.get("fileSizeKb");
                        }),
                names("comic_character", "Comic Characters", any(values -> BookFilterSpecification.withComicCollection("characters", values, idsFor(ComicCharacterEntity.class))),
                        (root, query, cb, scope) -> comic(root).join("characters", JoinType.LEFT).get("name")),
                names("comic_team", "Comic Teams", any(values -> BookFilterSpecification.withComicCollection("teams", values, idsFor(ComicTeamEntity.class))),
                        (root, query, cb, scope) -> comic(root).join("teams", JoinType.LEFT).get("name")),
                names("comic_location", "Comic Locations", any(values -> BookFilterSpecification.withComicCollection("locations", values, idsFor(ComicLocationEntity.class))),
                        (root, query, cb, scope) -> comic(root).join("locations", JoinType.LEFT).get("name")),
                names("comic_creator", "Comic Creators", any(values -> BookFilterSpecification.withComicCreators(values, idsFor(ComicCreatorEntity.class))),
                        (root, query, cb, scope) -> comic(root).join("creatorMappings", JoinType.LEFT).join("creator", JoinType.LEFT).get("name"))));
    }

    private static FacetFilter<BookEntity> any(Function<List<String>, Specification<BookEntity>> filter) {
        return (values, scope) -> filter.apply(values);
    }

    private static FacetDef<BookEntity> rating(String key, String title,
                                               Function<List<String>, Specification<BookEntity>> filter, String field) {
        return number(key, title, any(filter), (root, query, cb, scope) -> metadata(root).get(field)).banded(RATING_BANDS);
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

    private static Join<?, ?> metadata(Root<BookEntity> root) {
        return root.join("metadata", JoinType.LEFT);
    }

    private static Join<?, ?> comic(Root<BookEntity> root) {
        return metadata(root).join("comicMetadata", JoinType.LEFT);
    }

    private Map<String, Specification<BookEntity>> shelfStatuses(BrowseScope scope) {
        return Map.of(
                "shelved", BookFilterSpecification.withShelfStatus(List.of("shelved"), scope.userId()),
                "unshelved", BookFilterSpecification.withShelfStatus(List.of("unshelved"), scope.userId()));
    }

    private Map<String, Specification<BookEntity>> magicShelves(BrowseScope scope) {
        return magicShelfBookService.visibleShelfRules(scope.userId()).entrySet().stream()
                .collect(Collectors.toMap(rule -> MAGIC_SHELF_PREFIX + rule.getKey(), Map.Entry::getValue));
    }

    private Specification<BookEntity> shelves(List<String> values, BrowseScope scope) {
        List<String> regularIds = new ArrayList<>();
        List<Specification<BookEntity>> specs = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (value.startsWith(MAGIC_SHELF_PREFIX)) {
                specs.add(magicShelfBookService.toSpecification(scope.userId(), parseMagicShelfId(value)));
            } else {
                regularIds.add(value);
            }
        }
        if (!regularIds.isEmpty()) {
            specs.add(BookFilterSpecification.inShelves(regularIds, scope.userId()));
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
