package org.booklore.service.browse;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.FacetLogic;
import org.booklore.browse.Link;
import org.booklore.browse.ParamsHash;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.browse.FacetGroupsResponse;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetGroup;
import org.booklore.model.dto.browse.FacetGroupsResponse.FacetLink;
import org.booklore.model.dto.browse.FacetGroupsResponse.Metadata;
import org.booklore.model.dto.browse.FacetGroupsResponse.Properties;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookFacetService {

    private static final String PAGE_PATH = "/api/v1/books/page";
    private static final String FACET_PATH = "/api/v1/books/facets";
    private static final int MAX_VALUES = 100;

    private static final FacetDef PHYSICAL_FILE_TYPE = new FacetDef("file_type", "File Type", (cb, root, userId) ->
            cb.<String>selectCase()
                    .when(cb.isTrue(root.get("isPhysical")), "PHYSICAL")
                    .otherwise(cb.nullLiteral(String.class)));

    private static final List<FacetDef> FACETS = List.of(
            new FacetDef("author", "Authors", (cb, root, userId) -> metadata(root).join("authors", JoinType.LEFT).get("name")),
            new FacetDef("genre", "Genre", (cb, root, userId) -> metadata(root).join("categories", JoinType.LEFT).get("name")),
            new FacetDef("tag", "Tags", (cb, root, userId) -> metadata(root).join("tags", JoinType.LEFT).get("name")),
            new FacetDef("mood", "Moods", (cb, root, userId) -> metadata(root).join("moods", JoinType.LEFT).get("name")),
            new FacetDef("series", "Series", (cb, root, userId) -> metadata(root).get("seriesName")),
            new FacetDef("publisher", "Publisher", (cb, root, userId) -> metadata(root).get("publisher")),
            new FacetDef("language", "Language", (cb, root, userId) -> metadata(root).get("language")),
            new FacetDef("narrator", "Narrator", (cb, root, userId) -> metadata(root).get("narrator")),
            new FacetDef("file_type", "File Type", (cb, root, userId) -> {
                Join<BookEntity, BookFileEntity> files = root.join("bookFiles", JoinType.LEFT);
                files.on(cb.isTrue(files.get("isBookFormat")));
                return files.get("bookType");
            }),
            new FacetDef("content_rating", "Content Rating", (cb, root, userId) -> metadata(root).get("contentRating")),
            new FacetDef("amazon_rating", "Amazon Rating", (cb, root, userId) -> metadata(root).get("amazonRating")),
            new FacetDef("goodreads_rating", "Goodreads Rating", (cb, root, userId) -> metadata(root).get("goodreadsRating")),
            new FacetDef("hardcover_rating", "Hardcover Rating", (cb, root, userId) -> metadata(root).get("hardcoverRating")),
            new FacetDef("ranobedb_rating", "RanobeDB Rating", (cb, root, userId) -> metadata(root).get("ranobedbRating")),
            new FacetDef("lubimyczytac_rating", "Lubimyczytac Rating", (cb, root, userId) -> metadata(root).get("lubimyczytacRating")),
            new FacetDef("audible_rating", "Audible Rating", (cb, root, userId) -> metadata(root).get("audibleRating")),
            new FacetDef("applebooks_rating", "Apple Books Rating", (cb, root, userId) -> metadata(root).get("applebooksRating")),
            new FacetDef("age_rating", "Age Rating", (cb, root, userId) -> metadata(root).get("ageRating")),
            new FacetDef("page_count", "Page Count", (cb, root, userId) -> metadata(root).get("pageCount")),
            new FacetDef("match_score", "Match Score", (cb, root, userId) -> root.get("metadataMatchScore")),
            new FacetDef("published_year", "Published Year", (cb, root, userId) ->
                    cb.function("YEAR", Integer.class, metadata(root).get("publishedDate"))),
            new FacetDef("library", "Library", (cb, root, userId) -> root.join("library").get("id")),
            new FacetDef("shelf", "Shelf", (cb, root, userId) -> root.join("shelves", JoinType.LEFT).get("id")),
            new FacetDef("shelf_status", "Shelf Status", (cb, root, userId) -> cb.<String>selectCase()
                    .when(cb.isNotEmpty(root.get("shelves")), "shelved")
                    .otherwise("unshelved")),
            new FacetDef("read_status", "Read Status", (cb, root, userId) -> {
                Join<BookEntity, UserBookProgressEntity> progress = root.join("userBookProgress", JoinType.LEFT);
                progress.on(cb.equal(progress.get("user").get("id"), userId));
                return cb.<ReadStatus>selectCase()
                        .when(progress.get("id").isNull(), ReadStatus.UNSET)
                        .otherwise(progress.get("readStatus"));
            }),
            new FacetDef("personal_rating", "Personal Rating", (cb, root, userId) -> {
                Join<BookEntity, UserBookProgressEntity> progress = root.join("userBookProgress");
                progress.on(cb.equal(progress.get("user").get("id"), userId));
                return progress.get("personalRating");
            }),
            new FacetDef("file_size", "File Size", (cb, root, userId) -> {
                Join<BookEntity, BookFileEntity> files = root.join("bookFiles", JoinType.LEFT);
                files.on(cb.isTrue(files.get("isBookFormat")));
                return files.get("fileSizeKb");
            }),
            new FacetDef("comic_character", "Comic Characters", (cb, root, userId) -> metadata(root).join("comicMetadata", JoinType.LEFT).join("characters", JoinType.LEFT).get("name")),
            new FacetDef("comic_team", "Comic Teams", (cb, root, userId) -> metadata(root).join("comicMetadata", JoinType.LEFT).join("teams", JoinType.LEFT).get("name")),
            new FacetDef("comic_location", "Comic Locations", (cb, root, userId) -> metadata(root).join("comicMetadata", JoinType.LEFT).join("locations", JoinType.LEFT).get("name")),
            new FacetDef("comic_creator", "Comic Creators", (cb, root, userId) -> metadata(root).join("comicMetadata", JoinType.LEFT).join("creatorMappings", JoinType.LEFT).join("creator", JoinType.LEFT).get("name")));

    private static final Set<String> NUMBER_FACETS = Set.of(
            "page_count", "published_year", "file_size", "match_score",
            "amazon_rating", "goodreads_rating", "hardcover_rating", "ranobedb_rating",
            "lubimyczytac_rating", "audible_rating", "applebooks_rating");

    private static final List<String> RATING_BANDS = List.of("0..1", "1..2", "2..3", "3..4", "4..4.5", "4.5..*");
    private static final Map<String, List<String>> NUMBER_BANDS = Map.of(
            "match_score", List.of("95..*", "90..95", "80..90", "70..80", "50..70", "30..50", "0..30"),
            "amazon_rating", RATING_BANDS,
            "goodreads_rating", RATING_BANDS,
            "hardcover_rating", RATING_BANDS,
            "ranobedb_rating", RATING_BANDS,
            "lubimyczytac_rating", RATING_BANDS,
            "audible_rating", RATING_BANDS,
            "applebooks_rating", RATING_BANDS);

    private final AuthenticationService authenticationService;
    private final BookFilterSpecifications filterSpecifications;
    private final BookFacetRegistry facetRegistry;
    private final BookSortRegistry sortRegistry;

    @PersistenceContext
    private EntityManager entityManager;

    private final Cache<String, FacetGroupsResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(200)
            .build();

    public FacetGroupsResponse getFacets(List<String> facet, String facetLogicParam, String query) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();
        boolean isAdmin = user.getPermissions().isAdmin();
        Set<Long> libraryIds = BookFilterSpecifications.libraryIds(user);

        Map<String, List<String>> facets = BookFilterSpecifications.parseFacets(facet);
        FacetLogic facetLogic = FacetLogic.from(facetLogicParam);

        String cacheKey = userId + ":" + ParamsHash.compute(query, facets, facetLogic);
        return cache.get(cacheKey, key -> {
            String preserved = BrowseParams.preserved(facet, facetLogicParam, query);
            List<FacetGroup> groups = new ArrayList<>();
            groups.add(sortGroup(preserved));
            for (FacetDef def : FACETS) {
                Specification<BookEntity> base = filterSpecifications.base(query, facets, facetLogic, userId, isAdmin, libraryIds, def.key());
                if (NUMBER_FACETS.contains(def.key())) {
                    groups.add(numberGroup(def, base, userId, facet, preserved));
                    continue;
                }
                List<FacetCount> counts = count(def, base, userId);
                if ("file_type".equals(def.key())) {
                    counts = Stream.concat(counts.stream(), count(PHYSICAL_FILE_TYPE, base, userId).stream())
                            .sorted(Comparator.comparingLong(FacetCount::count).reversed().thenComparing(FacetCount::value))
                            .toList();
                }
                groups.add(toGroup(def, counts, null, null, facet, preserved));
            }
            List<Link> links = List.of(Link.json(List.of("self"), href(FACET_PATH, preserved)));
            return new FacetGroupsResponse(links, groups);
        });
    }

    // Package-private: lets tests reset the shared singleton cache between runs.
    void clearCache() {
        cache.invalidateAll();
    }

    private List<FacetCount> count(FacetDef def, Specification<BookEntity> base, Long userId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<BookEntity> root = cq.from(BookEntity.class);
        Expression<?> value = def.value().apply(cb, root, userId);
        Expression<Long> count = cb.countDistinct(root.get("id"));

        List<Predicate> predicates = new ArrayList<>();
        Predicate basePredicate = base.toPredicate(root, cq, cb);
        if (basePredicate != null) {
            predicates.add(basePredicate);
        }
        predicates.add(cb.isNotNull(value));

        cq.multiselect(value.alias("value"), count.alias("count"));
        cq.where(predicates.toArray(Predicate[]::new));
        cq.groupBy(value);
        cq.orderBy(cb.desc(count), cb.asc(value));

        return entityManager.createQuery(cq).setMaxResults(MAX_VALUES).getResultList().stream()
                .map(tuple -> new FacetCount(String.valueOf(tuple.get("value")), ((Number) tuple.get("count")).longValue()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private FacetGroup numberGroup(FacetDef def, Specification<BookEntity> base, Long userId, List<String> facet, String preserved) {
        List<String> bands = NUMBER_BANDS.getOrDefault(def.key(), List.of());
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<BookEntity> root = cq.from(BookEntity.class);
        Expression<Number> value = (Expression<Number>) def.value().apply(cb, root, userId);

        List<Selection<?>> columns = new ArrayList<>();
        columns.add(cb.min(value).alias("min"));
        columns.add(cb.max(value).alias("max"));
        for (String band : bands) {
            Specification<BookEntity> inBand = facetRegistry.toSpecification(def.key(), List.of(band), FacetLogic.OR, userId);
            columns.add(countMatching(inBand, root, cq, cb).alias(band));
        }
        cq.multiselect(columns);
        Predicate basePredicate = base.toPredicate(root, cq, cb);
        if (basePredicate != null) {
            cq.where(basePredicate);
        }

        Tuple row = entityManager.createQuery(cq).getSingleResult();
        List<FacetCount> counts = bands.stream()
                .map(band -> new FacetCount(band, row.get(band, Long.class)))
                .toList();
        return toGroup(def, counts, row.get("min", Number.class), row.get("max", Number.class), facet, preserved);
    }

    // COUNT(DISTINCT CASE WHEN <filter matches> THEN id END), so one query can count several filters at once
    private Expression<Long> countMatching(Specification<BookEntity> filter, Root<BookEntity> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
        Expression<Long> matchingId = cb.<Long>selectCase()
                .when(filter.toPredicate(root, cq, cb), root.get("id"))
                .otherwise(cb.nullLiteral(Long.class));
        return cb.countDistinct(matchingId);
    }

    private FacetGroup toGroup(FacetDef def, List<FacetCount> counts, Number min, Number max, List<String> facet, String preserved) {
        List<FacetLink> links = counts.stream()
                .map(c -> {
                    boolean active = BrowseParams.hasFacet(facet, def.key(), c.value());
                    List<String> rel = active ? List.of("self", "facet") : List.of("facet");
                    String href = active
                            ? href(PAGE_PATH, preserved)
                            : pageLink(preserved, "facet=" + BrowseParams.encode(def.key() + ":" + c.value()));
                    return new FacetLink(rel, href, Link.JSON_TYPE, c.value(), c.value(), new Properties(c.count()));
                })
                .toList();
        return new FacetGroup(new Metadata("facet", def.key(), def.title(), min, max), links);
    }

    private FacetGroup sortGroup(String preserved) {
        List<FacetLink> links = new ArrayList<>();
        for (String key : sortRegistry.registry().keys()) {
            if (key.equals("id")) {
                continue;
            }
            links.add(new FacetLink(List.of("sort"), pageLink(preserved, "sort=" + BrowseParams.encode(key)), Link.JSON_TYPE, key + " ascending", key, null));
            links.add(new FacetLink(List.of("sort"), pageLink(preserved, "sort=-" + BrowseParams.encode(key)), Link.JSON_TYPE, key + " descending", "-" + key, null));
        }
        return new FacetGroup(new Metadata("sort", "sort", "Sort"), links);
    }

    private static String pageLink(String preserved, String param) {
        return preserved.isBlank() ? PAGE_PATH + "?" + param : PAGE_PATH + "?" + preserved + "&" + param;
    }

    private static String href(String path, String preserved) {
        return preserved.isBlank() ? path : path + "?" + preserved;
    }

    private static Join<?, ?> metadata(Root<BookEntity> root) {
        return root.join("metadata", JoinType.LEFT);
    }

    private interface FacetValueSource {
        Expression<?> apply(CriteriaBuilder cb, Root<BookEntity> root, Long userId);
    }

    private record FacetDef(String key, String title, FacetValueSource value) {
    }

    private record FacetCount(String value, long count) {
    }
}
