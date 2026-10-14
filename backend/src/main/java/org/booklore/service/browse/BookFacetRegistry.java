package org.booklore.service.browse;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

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

    public BookFacetRegistry(MagicShelfBookService magicShelfBookService) {
        this.magicShelfBookService = magicShelfBookService;
        this.registry = build();
    }

    public FacetRegistry<BookEntity> registry() {
        return registry;
    }

    private FacetRegistry<BookEntity> build() {
        return new FacetRegistry<BookEntity>()
                .register(names("author", "Authors", any(BookFilterSpecification::withAuthors),
                        (root, query, cb, scope) -> metadata(root).join("authors", JoinType.LEFT).get("name")))
                .register(names("genre", "Genre", any(BookFilterSpecification::withCategories),
                        (root, query, cb, scope) -> metadata(root).join("categories", JoinType.LEFT).get("name")))
                .register(names("tag", "Tags", any(BookFilterSpecification::withTags),
                        (root, query, cb, scope) -> metadata(root).join("tags", JoinType.LEFT).get("name")))
                .register(names("mood", "Moods", any(BookFilterSpecification::withMoods),
                        (root, query, cb, scope) -> metadata(root).join("moods", JoinType.LEFT).get("name")))
                .register(names("series", "Series", any(BookFilterSpecification::inSeriesMulti),
                        (root, query, cb, scope) -> metadata(root).get("seriesName")))
                .register(names("publisher", "Publisher", any(BookFilterSpecification::withPublishers),
                        (root, query, cb, scope) -> metadata(root).get("publisher")))
                .register(names("language", "Language", any(BookFilterSpecification::withLanguages),
                        (root, query, cb, scope) -> metadata(root).get("language")))
                .register(names("narrator", "Narrator", any(BookFilterSpecification::withNarrators),
                        (root, query, cb, scope) -> metadata(root).get("narrator")))
                .register(values("file_type", "File Type", any(BookFilterSpecification::withFileTypes),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, BookFileEntity> files = root.join("bookFiles", JoinType.LEFT);
                            files.on(cb.isTrue(files.get("isBookFormat")));
                            return files.get("bookType");
                        },
                        (root, query, cb, scope) -> cb.<String>selectCase()
                                .when(cb.isTrue(root.get("isPhysical")), "PHYSICAL")
                                .otherwise(cb.nullLiteral(String.class))))
                .register(values("content_rating", "Content Rating", any(BookFilterSpecification::withContentRatings),
                        (root, query, cb, scope) -> metadata(root).get("contentRating")))
                .register(rating("amazon_rating", "Amazon Rating", BookFilterSpecification::withAmazonRatings, "amazonRating"))
                .register(rating("goodreads_rating", "Goodreads Rating", BookFilterSpecification::withGoodreadsRatings, "goodreadsRating"))
                .register(rating("hardcover_rating", "Hardcover Rating", BookFilterSpecification::withHardcoverRatings, "hardcoverRating"))
                .register(rating("ranobedb_rating", "RanobeDB Rating", BookFilterSpecification::withRanobedbRatings, "ranobedbRating"))
                .register(rating("lubimyczytac_rating", "Lubimyczytac Rating", BookFilterSpecification::withLubimyczytacRatings, "lubimyczytacRating"))
                .register(rating("audible_rating", "Audible Rating", BookFilterSpecification::withAudibleRatings, "audibleRating"))
                .register(rating("applebooks_rating", "Apple Books Rating", BookFilterSpecification::withApplebooksRatings, "applebooksRating"))
                .register(values("age_rating", "Age Rating", any(BookFilterSpecification::withAgeRatings),
                        (root, query, cb, scope) -> metadata(root).get("ageRating")))
                .register(number("page_count", "Page Count", any(BookFilterSpecification::withPageCounts),
                        (root, query, cb, scope) -> metadata(root).get("pageCount")))
                .register(number("match_score", "Match Score", any(BookFilterSpecification::withMatchScores),
                        (root, query, cb, scope) -> root.get("metadataMatchScore")).banded(MATCH_SCORE_BANDS))
                .register(number("published_year", "Published Year", any(BookFilterSpecification::withPublishedYears),
                        (root, query, cb, scope) -> cb.function("YEAR", Integer.class, metadata(root).get("publishedDate"))))
                .register(values("library", "Library", any(BookFilterSpecification::inLibraries),
                        (root, query, cb, scope) -> root.join("library").get("id")))
                .register(values("shelf", "Shelf", this::shelves,
                        (root, query, cb, scope) -> root.join("shelves", JoinType.LEFT).get("id")))
                .register(values("shelf_status", "Shelf Status", any(BookFilterSpecification::withShelfStatus),
                        (root, query, cb, scope) -> cb.<String>selectCase()
                                .when(cb.isNotEmpty(root.get("shelves")), "shelved")
                                .otherwise("unshelved")))
                .register(values("read_status", "Read Status",
                        (values, scope) -> BookFilterSpecification.withReadStatuses(values, scope.userId(), "or"),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, UserBookProgressEntity> progress = root.join("userBookProgress", JoinType.LEFT);
                            progress.on(cb.equal(progress.get("user").get("id"), scope.userId()));
                            return cb.<ReadStatus>selectCase()
                                    .when(progress.get("id").isNull(), ReadStatus.UNSET)
                                    .otherwise(progress.get("readStatus"));
                        }))
                .register(values("personal_rating", "Personal Rating",
                        (values, scope) -> BookFilterSpecification.withPersonalRatings(values, scope.userId(), "or"),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, UserBookProgressEntity> progress = root.join("userBookProgress");
                            progress.on(cb.equal(progress.get("user").get("id"), scope.userId()));
                            return progress.get("personalRating");
                        }))
                .register(number("file_size", "File Size", any(BookFilterSpecification::withFileSizes),
                        (root, query, cb, scope) -> {
                            Join<BookEntity, BookFileEntity> files = root.join("bookFiles", JoinType.LEFT);
                            files.on(cb.isTrue(files.get("isBookFormat")));
                            return files.get("fileSizeKb");
                        }))
                .register(names("comic_character", "Comic Characters", any(BookFilterSpecification::withComicCharacters),
                        (root, query, cb, scope) -> comic(root).join("characters", JoinType.LEFT).get("name")))
                .register(names("comic_team", "Comic Teams", any(BookFilterSpecification::withComicTeams),
                        (root, query, cb, scope) -> comic(root).join("teams", JoinType.LEFT).get("name")))
                .register(names("comic_location", "Comic Locations", any(BookFilterSpecification::withComicLocations),
                        (root, query, cb, scope) -> comic(root).join("locations", JoinType.LEFT).get("name")))
                .register(names("comic_creator", "Comic Creators", any(BookFilterSpecification::withComicCreators),
                        (root, query, cb, scope) -> comic(root).join("creatorMappings", JoinType.LEFT).join("creator", JoinType.LEFT).get("name")));
    }

    private static FacetFilter<BookEntity> any(BiFunction<List<String>, String, Specification<BookEntity>> filter) {
        return (values, scope) -> filter.apply(values, "or");
    }

    private static FacetDef<BookEntity> rating(String key, String title,
                                               BiFunction<List<String>, String, Specification<BookEntity>> filter, String field) {
        return number(key, title, any(filter), (root, query, cb, scope) -> metadata(root).get(field)).banded(RATING_BANDS);
    }

    private static Join<?, ?> metadata(Root<BookEntity> root) {
        return root.join("metadata", JoinType.LEFT);
    }

    private static Join<?, ?> comic(Root<BookEntity> root) {
        return metadata(root).join("comicMetadata", JoinType.LEFT);
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
            specs.add(BookFilterSpecification.inShelves(regularIds, "or"));
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
