package org.booklore.service;

import jakarta.persistence.criteria.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.GroupRule;
import org.booklore.model.dto.Rule;
import org.booklore.model.dto.RuleField;
import org.booklore.model.dto.RuleOperator;
import org.booklore.model.entity.*;
import org.booklore.model.enums.ComicCreatorRole;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class BookRuleEvaluatorService {

    private final ObjectMapper objectMapper;

    private static final Set<RuleField> COMPOSITE_FIELDS = Set.of(
            RuleField.SERIES_STATUS, RuleField.SERIES_GAPS, RuleField.SERIES_POSITION
    );

    public Specification<BookEntity> toSpecification(GroupRule groupRule, Long userId) {
        return (root, query, cb) -> {
            Join<BookEntity, UserBookProgressEntity> progressJoin = root.join("userBookProgress", JoinType.LEFT);

            Predicate userPredicate = cb.or(
                    cb.isNull(progressJoin.get("user").get("id")),
                    cb.equal(progressJoin.get("user").get("id"), userId)
            );

            Predicate rulePredicate = buildPredicate(groupRule, query, cb, root, progressJoin, userId);

            return cb.and(userPredicate, rulePredicate);
        };
    }

    private Predicate buildPredicate(GroupRule group, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin, Long userId) {
        if (group.getRules() == null || group.getRules().isEmpty()) {
            return cb.conjunction();
        }

        List<Predicate> predicates = new ArrayList<>();

        for (Object ruleObj : group.getRules()) {
            if (ruleObj == null) continue;

            Map<String, Object> ruleMap = objectMapper.convertValue(ruleObj, new TypeReference<>() {
            });
            String type = (String) ruleMap.get("type");

            if ("group".equals(type)) {
                GroupRule subGroup = objectMapper.convertValue(ruleObj, GroupRule.class);
                predicates.add(buildPredicate(subGroup, query, cb, root, progressJoin, userId));
            } else {
                try {
                    Rule rule = objectMapper.convertValue(ruleObj, Rule.class);
                    Predicate rulePredicate = buildRulePredicate(rule, query, cb, root, progressJoin, userId);
                    if (rulePredicate != null) {
                        predicates.add(rulePredicate);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse rule: {}, error: {}", ruleObj, e.getMessage(), e);
                }
            }
        }

        if (predicates.isEmpty()) {
            return cb.conjunction();
        }

        return group.getJoin() == org.booklore.model.dto.JoinType.AND
                ? cb.and(predicates.toArray(new Predicate[0]))
                : cb.or(predicates.toArray(new Predicate[0]));
    }

    private Predicate buildRulePredicate(Rule rule, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin, Long userId) {
        if (rule.getField() == null || rule.getOperator() == null) return null;

        if (rule.getField() == RuleField.METADATA_PRESENCE) {
            return buildMetadataPresencePredicate(rule, query, cb, root, progressJoin);
        }

        if (COMPOSITE_FIELDS.contains(rule.getField())) {
            return buildCompositeFieldPredicate(rule, query, cb, root, progressJoin, userId);
        }

        return switch (rule.getOperator()) {
            case EQUALS -> buildEquals(rule, cb, root, progressJoin);
            case NOT_EQUALS -> buildNotEquals(rule, cb, root, progressJoin);
            case CONTAINS -> buildContains(rule, cb, root, progressJoin);
            case DOES_NOT_CONTAIN -> cb.not(buildContains(rule, cb, root, progressJoin));
            case STARTS_WITH -> buildStartsWith(rule, cb, root, progressJoin);
            case ENDS_WITH -> buildEndsWith(rule, cb, root, progressJoin);
            case GREATER_THAN -> buildGreaterThan(rule, cb, root, progressJoin);
            case GREATER_THAN_EQUAL_TO -> buildGreaterThanEqual(rule, cb, root, progressJoin);
            case LESS_THAN -> buildLessThan(rule, cb, root, progressJoin);
            case LESS_THAN_EQUAL_TO -> buildLessThanEqual(rule, cb, root, progressJoin);
            case IN_BETWEEN -> buildInBetween(rule, cb, root, progressJoin);
            case IS_EMPTY -> buildIsEmpty(rule, query, cb, root, progressJoin);
            case IS_NOT_EMPTY -> cb.not(buildIsEmpty(rule, query, cb, root, progressJoin));
            case INCLUDES_ANY -> buildIncludesAny(rule, cb, root, progressJoin);
            case EXCLUDES_ALL -> buildExcludesAll(rule, cb, root, progressJoin);
            case INCLUDES_ALL -> buildIncludesAll(rule, cb, root, progressJoin);
            case WITHIN_LAST -> buildWithinLast(rule, cb, root, progressJoin);
            case OLDER_THAN -> buildOlderThan(rule, cb, root, progressJoin);
            case THIS_PERIOD -> buildThisPeriod(rule, cb, root, progressJoin);
        };
    }

    private Predicate buildWithinLast(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Instant threshold = computeRelativeDateThreshold(rule);
        if (threshold == null) return cb.conjunction();

        if (rule.getField() == RuleField.PUBLISHED_DATE) {
            LocalDateTime ldt = threshold.atZone(ZoneId.systemDefault()).toLocalDateTime();
            return cb.greaterThanOrEqualTo(field.as(LocalDateTime.class), ldt);
        }
        return cb.greaterThanOrEqualTo(field.as(Instant.class), threshold);
    }

    private Predicate buildOlderThan(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Instant threshold = computeRelativeDateThreshold(rule);
        if (threshold == null) return cb.conjunction();

        if (rule.getField() == RuleField.PUBLISHED_DATE) {
            LocalDateTime ldt = threshold.atZone(ZoneId.systemDefault()).toLocalDateTime();
            return cb.lessThan(field.as(LocalDateTime.class), ldt);
        }
        return cb.lessThan(field.as(Instant.class), threshold);
    }

    private Predicate buildThisPeriod(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        String period = rule.getValue() != null ? rule.getValue().toString().toLowerCase() : "year";
        LocalDate now = LocalDate.now();
        LocalDate start = switch (period) {
            case "week" -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> now.withDayOfMonth(1);
            default -> now.withDayOfYear(1);
        };

        Instant startInstant = start.atStartOfDay(ZoneId.systemDefault()).toInstant();

        if (rule.getField() == RuleField.PUBLISHED_DATE) {
            LocalDateTime ldt = startInstant.atZone(ZoneId.systemDefault()).toLocalDateTime();
            return cb.greaterThanOrEqualTo(field.as(LocalDateTime.class), ldt);
        }
        return cb.greaterThanOrEqualTo(field.as(Instant.class), startInstant);
    }

    private Instant computeRelativeDateThreshold(Rule rule) {
        if (rule.getValue() == null) return null;
        int amount;
        try {
            amount = ((Number) rule.getValue()).intValue();
        } catch (ClassCastException e) {
            try {
                amount = Integer.parseInt(rule.getValue().toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        String unit = rule.getValueEnd() != null ? rule.getValueEnd().toString().toLowerCase() : "days";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = switch (unit) {
            case "weeks" -> now.minusWeeks(amount);
            case "months" -> now.minusMonths(amount);
            case "years" -> now.minusYears(amount);
            default -> now.minusDays(amount);
        };
        return threshold.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Predicate buildCompositeFieldPredicate(Rule rule, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin, Long userId) {
        boolean negate = rule.getOperator() == RuleOperator.NOT_EQUALS;
        String value = rule.getValue() != null ? rule.getValue().toString().toLowerCase() : "";
        Predicate hasSeries = cb.and(
                cb.isNotNull(root.get("metadata").get("seriesName")),
                cb.notEqual(cb.trim(root.get("metadata").get("seriesName").as(String.class)), "")
        );

        Predicate result = switch (rule.getField()) {
            case SERIES_STATUS -> buildSeriesStatusPredicate(value, query, cb, root, hasSeries, userId);
            case SERIES_GAPS -> buildSeriesGapsPredicate(value, query, cb, root, hasSeries);
            case SERIES_POSITION -> buildSeriesPositionPredicate(value, query, cb, root, progressJoin, hasSeries, userId);
            default -> cb.conjunction();
        };

        return negate ? cb.not(result) : result;
    }

    private Predicate buildMetadataPresencePredicate(Rule rule, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        boolean hasOperator = rule.getOperator() == RuleOperator.EQUALS;
        String metadataField = rule.getValue() != null ? rule.getValue().toString() : "";
        Predicate isPresent = buildFieldPresencePredicate(metadataField, query, cb, root, progressJoin);
        return hasOperator ? isPresent : cb.not(isPresent);
    }

    private Predicate buildFieldPresencePredicate(String metadataField, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return switch (metadataField) {
            // Cover image — stored as hash on BookEntity
            case "thumbnailUrl" -> cb.isNotNull(root.get("bookCoverHash"));

            // Personal rating — on progress join
            case "personalRating" -> cb.isNotNull(progressJoin.get("personalRating"));

            // Audiobook duration — on BookFileEntity
            case "audiobookDuration" -> {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<BookFileEntity> subRoot = sub.from(BookFileEntity.class);
                sub.select(cb.literal(1L)).where(
                        cb.equal(subRoot.get("book").get("id"), root.get("id")),
                        cb.isNotNull(subRoot.get("durationSeconds"))
                );
                yield cb.exists(sub);
            }

            // Collection fields on BookMetadataEntity
            case "authors" -> collectionPresence(query, cb, root, "authors");
            case "categories" -> collectionPresence(query, cb, root, "categories");
            case "moods" -> collectionPresence(query, cb, root, "moods");
            case "tags" -> collectionPresence(query, cb, root, "tags");

            // Comic collection fields
            case "comicCharacters" -> comicCollectionPresence(query, cb, root, "characters");
            case "comicTeams" -> comicCollectionPresence(query, cb, root, "teams");
            case "comicLocations" -> comicCollectionPresence(query, cb, root, "locations");

            // Comic creator role fields
            case "comicPencillers" -> comicCreatorPresence(query, cb, root, ComicCreatorRole.PENCILLER);
            case "comicInkers" -> comicCreatorPresence(query, cb, root, ComicCreatorRole.INKER);
            case "comicColorists" -> comicCreatorPresence(query, cb, root, ComicCreatorRole.COLORIST);
            case "comicLetterers" -> comicCreatorPresence(query, cb, root, ComicCreatorRole.LETTERER);
            case "comicCoverArtists" -> comicCreatorPresence(query, cb, root, ComicCreatorRole.COVER_ARTIST);
            case "comicEditors" -> comicCreatorPresence(query, cb, root, ComicCreatorRole.EDITOR);

            // String fields on BookMetadataEntity
            case "title", "subtitle", "description", "publisher", "language", "seriesName",
                 "isbn13", "isbn10", "asin", "contentRating", "narrator",
                 "goodreadsId", "hardcoverId", "googleId", "audibleId",
                 "lubimyczytacId", "ranobedbId", "comicvineId" ->
                    stringPresence(cb, root.get("metadata").get(metadataField));

            // Numeric/date/boolean fields on BookMetadataEntity
            case "pageCount", "seriesNumber", "seriesTotal", "ageRating", "publishedDate", "abridged",
                 "amazonRating", "goodreadsRating", "hardcoverRating", "ranobedbRating",
                 "lubimyczytacRating", "audibleRating",
                 "amazonReviewCount", "goodreadsReviewCount", "hardcoverReviewCount", "audibleReviewCount" ->
                    cb.isNotNull(root.get("metadata").get(metadataField));

            default -> cb.conjunction();
        };
    }

    private Predicate stringPresence(CriteriaBuilder cb, Expression<?> field) {
        return cb.and(cb.isNotNull(field), cb.notEqual(cb.trim(field.as(String.class)), ""));
    }

    private Predicate collectionPresence(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, String collectionName) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookEntity> subRoot = sub.from(BookEntity.class);
        Join<Object, Object> metadataJoin = subRoot.join("metadata", JoinType.INNER);
        metadataJoin.join(collectionName, JoinType.INNER);
        sub.select(cb.literal(1L)).where(cb.equal(subRoot.get("id"), root.get("id")));
        return cb.exists(sub);
    }

    private Predicate comicCollectionPresence(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, String collectionName) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookEntity> subRoot = sub.from(BookEntity.class);
        Join<Object, Object> metadataJoin = subRoot.join("metadata", JoinType.INNER);
        Join<Object, Object> comicJoin = metadataJoin.join("comicMetadata", JoinType.INNER);
        comicJoin.join(collectionName, JoinType.INNER);
        sub.select(cb.literal(1L)).where(cb.equal(subRoot.get("id"), root.get("id")));
        return cb.exists(sub);
    }

    private Predicate comicCreatorPresence(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, ComicCreatorRole role) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<ComicCreatorMappingEntity> subRoot = sub.from(ComicCreatorMappingEntity.class);
        sub.select(cb.literal(1L)).where(
                cb.equal(subRoot.get("comicMetadata").get("bookId"), root.get("id")),
                cb.equal(subRoot.get("role"), role)
        );
        return cb.exists(sub);
    }

    private Predicate buildSeriesStatusPredicate(String value, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Predicate hasSeries, Long userId) {
        Predicate condition = switch (value) {
            case "reading" -> seriesHasReadStatus(query, cb, root, userId, List.of("READING", "RE_READING"));
            case "not_started" -> cb.not(seriesHasReadStatus(query, cb, root, userId, List.of("READ", "READING", "RE_READING", "PARTIALLY_READ")));
            case "fully_read" -> seriesAllRead(query, cb, root, userId);
            case "completed" -> seriesOwnsLastBook(query, cb, root);
            case "ongoing" -> cb.and(seriesHasTotal(query, cb, root), cb.not(seriesOwnsLastBook(query, cb, root)));
            default -> cb.conjunction();
        };
        return cb.and(hasSeries, condition);
    }

    private Predicate seriesHasReadStatus(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Long userId, List<String> statuses) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookEntity> subRoot = sub.from(BookEntity.class);
        Join<Object, Object> subProgress = subRoot.join("userBookProgress", JoinType.INNER);

        sub.select(cb.literal(1L)).where(
                cb.equal(subRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.equal(subProgress.get("user").get("id"), userId),
                subProgress.get("readStatus").as(String.class).in(statuses)
        );
        return cb.exists(sub);
    }

    private Predicate seriesAllRead(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Long userId) {
        Subquery<Long> notReadSub = query.subquery(Long.class);
        Root<BookEntity> nrRoot = notReadSub.from(BookEntity.class);
        Join<Object, Object> nrProgress = nrRoot.join("userBookProgress", JoinType.INNER);
        notReadSub.select(cb.literal(1L)).where(
                cb.equal(nrRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.equal(nrProgress.get("user").get("id"), userId),
                cb.notEqual(nrProgress.get("readStatus").as(String.class), "READ")
        );

        return cb.and(
                seriesHasReadStatus(query, cb, root, userId, List.of("READ")),
                cb.not(cb.exists(notReadSub))
        );
    }

    private Predicate seriesOwnsLastBook(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root) {
        Subquery<Integer> totalSub = query.subquery(Integer.class);
        Root<BookEntity> totalRoot = totalSub.from(BookEntity.class);
        totalSub.select(cb.max(totalRoot.get("metadata").get("seriesTotal"))).where(
                cb.equal(totalRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(totalRoot.get("metadata").get("seriesTotal"))
        );

        Subquery<Long> existsSub = query.subquery(Long.class);
        Root<BookEntity> subRoot = existsSub.from(BookEntity.class);
        existsSub.select(cb.literal(1L)).where(
                cb.equal(subRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.equal(
                        cb.function("FLOOR", Integer.class, subRoot.get("metadata").get("seriesNumber")),
                        totalSub
                )
        );
        return cb.exists(existsSub);
    }

    private Predicate seriesHasTotal(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookEntity> subRoot = sub.from(BookEntity.class);
        sub.select(cb.literal(1L)).where(
                cb.equal(subRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(subRoot.get("metadata").get("seriesTotal"))
        );
        return cb.exists(sub);
    }

    private Predicate buildSeriesGapsPredicate(String value, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Predicate hasSeries) {
        Predicate condition = switch (value) {
            case "any_gap" -> seriesHasAnyGap(query, cb, root);
            case "missing_first" -> seriesMissingFirst(query, cb, root);
            case "missing_latest" -> cb.and(seriesHasTotal(query, cb, root), cb.not(seriesOwnsLastBook(query, cb, root)));
            case "duplicate_number" -> seriesHasDuplicateNumber(query, cb, root);
            default -> cb.conjunction();
        };
        return cb.and(hasSeries, condition);
    }

    private Predicate seriesHasAnyGap(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root) {
        Subquery<Long> countSub = query.subquery(Long.class);
        Root<BookEntity> cRoot = countSub.from(BookEntity.class);
        countSub.select(cb.countDistinct(cb.function("FLOOR", Integer.class, cRoot.get("metadata").get("seriesNumber")))).where(
                cb.equal(cRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(cRoot.get("metadata").get("seriesNumber"))
        );

        Subquery<Integer> maxSub = query.subquery(Integer.class);
        Root<BookEntity> mRoot = maxSub.from(BookEntity.class);
        maxSub.select(cb.max(cb.function("FLOOR", Integer.class, mRoot.get("metadata").get("seriesNumber")))).where(
                cb.equal(mRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(mRoot.get("metadata").get("seriesNumber"))
        );

        return cb.lt(countSub, maxSub.as(Long.class));
    }

    private Predicate seriesMissingFirst(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<BookEntity> subRoot = sub.from(BookEntity.class);
        sub.select(cb.literal(1L)).where(
                cb.equal(subRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.equal(cb.function("FLOOR", Integer.class, subRoot.get("metadata").get("seriesNumber")), 1)
        );
        return cb.not(cb.exists(sub));
    }

    private Predicate seriesHasDuplicateNumber(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root) {
        Subquery<Long> totalSub = query.subquery(Long.class);
        Root<BookEntity> tRoot = totalSub.from(BookEntity.class);
        totalSub.select(cb.count(tRoot)).where(
                cb.equal(tRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(tRoot.get("metadata").get("seriesNumber"))
        );

        Subquery<Long> distinctSub = query.subquery(Long.class);
        Root<BookEntity> dRoot = distinctSub.from(BookEntity.class);
        distinctSub.select(cb.countDistinct(dRoot.get("metadata").get("seriesNumber"))).where(
                cb.equal(dRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(dRoot.get("metadata").get("seriesNumber"))
        );

        return cb.gt(totalSub, distinctSub);
    }

    private Predicate buildSeriesPositionPredicate(String value, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin, Predicate hasSeries, Long userId) {
        Predicate hasNumber = cb.isNotNull(root.get("metadata").get("seriesNumber"));
        Predicate condition = switch (value) {
            case "next_unread" -> isNextUnread(query, cb, root, progressJoin, userId);
            case "first_in_series" -> isFirstInSeries(query, cb, root);
            case "last_in_series" -> isLastInSeries(query, cb, root);
            default -> cb.conjunction();
        };
        return cb.and(hasSeries, hasNumber, condition);
    }

    private Predicate isFirstInSeries(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root) {
        Subquery<Float> minSub = query.subquery(Float.class);
        Root<BookEntity> mRoot = minSub.from(BookEntity.class);
        minSub.select(cb.min(mRoot.get("metadata").get("seriesNumber"))).where(
                cb.equal(mRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(mRoot.get("metadata").get("seriesNumber"))
        );
        return cb.equal(root.get("metadata").get("seriesNumber"), minSub);
    }

    private Predicate isLastInSeries(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root) {
        Subquery<Float> maxSub = query.subquery(Float.class);
        Root<BookEntity> mRoot = maxSub.from(BookEntity.class);
        maxSub.select(cb.max(mRoot.get("metadata").get("seriesNumber"))).where(
                cb.equal(mRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(mRoot.get("metadata").get("seriesNumber"))
        );
        return cb.equal(root.get("metadata").get("seriesNumber"), maxSub);
    }

    private Predicate isNextUnread(CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin, Long userId) {
        Predicate notRead = cb.or(
                cb.isNull(progressJoin.get("readStatus")),
                cb.notEqual(progressJoin.get("readStatus").as(String.class), "READ")
        );

        Subquery<Long> lowerUnreadSub = query.subquery(Long.class);
        Root<BookEntity> luRoot = lowerUnreadSub.from(BookEntity.class);
        Join<Object, Object> luProgress = luRoot.join("userBookProgress", JoinType.LEFT);
        lowerUnreadSub.select(cb.literal(1L)).where(
                cb.equal(luRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(luRoot.get("metadata").get("seriesNumber")),
                cb.lt(luRoot.get("metadata").get("seriesNumber"), root.get("metadata").get("seriesNumber")),
                cb.or(
                        cb.isNull(luProgress.get("readStatus")),
                        cb.notEqual(luProgress.get("readStatus").as(String.class), "READ")
                ),
                cb.or(
                        cb.isNull(luProgress.get("user").get("id")),
                        cb.equal(luProgress.get("user").get("id"), userId)
                )
        );
        Predicate noLowerUnread = cb.not(cb.exists(lowerUnreadSub));

        Subquery<Long> priorReadSub = query.subquery(Long.class);
        Root<BookEntity> prRoot = priorReadSub.from(BookEntity.class);
        Join<Object, Object> prProgress = prRoot.join("userBookProgress", JoinType.INNER);
        priorReadSub.select(cb.literal(1L)).where(
                cb.equal(prRoot.get("metadata").get("seriesName"), root.get("metadata").get("seriesName")),
                cb.isNotNull(prRoot.get("metadata").get("seriesNumber")),
                cb.lt(prRoot.get("metadata").get("seriesNumber"), root.get("metadata").get("seriesNumber")),
                cb.equal(prProgress.get("user").get("id"), userId),
                cb.equal(prProgress.get("readStatus").as(String.class), "READ")
        );
        Predicate hasPriorRead = cb.exists(priorReadSub);

        return cb.and(notRead, noLowerUnread, hasPriorRead);
    }

    private Predicate buildEquals(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, false);
        }

        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Object value = normalizeValue(rule.getValue(), rule.getField());

        if (value instanceof Boolean) {
            return cb.equal(field, value);
        } else if (value instanceof LocalDateTime) {
            return cb.equal(field, value);
        } else if (rule.getField() == RuleField.READ_STATUS) {
            if ("UNSET".equals(value.toString())) {
                return cb.isNull(field);
            }
            return cb.equal(field, value.toString());
        } else if (value instanceof Number) {
            return cb.equal(field, value);
        }
        return cb.equal(cb.lower(field.as(String.class)), value.toString().toLowerCase());
    }

    private Predicate buildNotEquals(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return cb.not(buildEquals(rule, cb, root, progressJoin));
    }

    private Predicate buildContains(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        String ruleVal = rule.getValue().toString().toLowerCase();
        return buildStringPredicate(rule.getField(), root, progressJoin, cb,
                nameField -> cb.like(cb.lower(nameField), "%" + escapeLike(ruleVal) + "%"));
    }

    private Predicate buildStartsWith(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        String ruleVal = rule.getValue().toString().toLowerCase();
        return buildStringPredicate(rule.getField(), root, progressJoin, cb,
                nameField -> cb.like(cb.lower(nameField), escapeLike(ruleVal) + "%"));
    }

    private Predicate buildEndsWith(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        String ruleVal = rule.getValue().toString().toLowerCase();
        return buildStringPredicate(rule.getField(), root, progressJoin, cb,
                nameField -> cb.like(cb.lower(nameField), "%" + escapeLike(ruleVal)));
    }

    private Predicate buildStringPredicate(RuleField field, Root<BookEntity> root,
                                           Join<BookEntity, UserBookProgressEntity> progressJoin,
                                           CriteriaBuilder cb,
                                           java.util.function.Function<Expression<String>, Predicate> predicateBuilder) {
        if (isArrayField(field)) {
            Join<?, ?> arrayJoin = createArrayFieldJoin(field, root);
            Expression<String> nameField = getArrayFieldNameExpression(field, arrayJoin);
            return predicateBuilder.apply(nameField);
        }

        Expression<?> fieldExpr = getFieldExpression(field, cb, root, progressJoin);
        if (fieldExpr == null) return cb.conjunction();

        return predicateBuilder.apply(fieldExpr.as(String.class));
    }

    private Predicate buildGreaterThan(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
                (field, dateValue) -> cb.greaterThan(field.as(LocalDateTime.class), dateValue),
                (field, numValue) -> cb.gt(field.as(Double.class), numValue));
    }

    private Predicate buildGreaterThanEqual(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
                (field, dateValue) -> cb.greaterThanOrEqualTo(field.as(LocalDateTime.class), dateValue),
                (field, numValue) -> cb.ge(field.as(Double.class), numValue));
    }

    private Predicate buildLessThan(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
                (field, dateValue) -> cb.lessThan(field.as(LocalDateTime.class), dateValue),
                (field, numValue) -> cb.lt(field.as(Double.class), numValue));
    }

    private Predicate buildLessThanEqual(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return buildComparisonPredicate(rule, cb, root, progressJoin,
                (field, dateValue) -> cb.lessThanOrEqualTo(field.as(LocalDateTime.class), dateValue),
                (field, numValue) -> cb.le(field.as(Double.class), numValue));
    }

    private Predicate buildComparisonPredicate(Rule rule, CriteriaBuilder cb, Root<BookEntity> root,
                                               Join<BookEntity, UserBookProgressEntity> progressJoin,
                                               BiFunction<Expression<?>, LocalDateTime, Predicate> dateComparator,
                                               BiFunction<Expression<?>, Double, Predicate> numberComparator) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Object value = normalizeValue(rule.getValue(), rule.getField());

        if (value instanceof LocalDateTime) {
            return dateComparator.apply(field, (LocalDateTime) value);
        }
        return numberComparator.apply(field, ((Number) value).doubleValue());
    }

    private Predicate buildInBetween(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        Object start = normalizeValue(rule.getValueStart(), rule.getField());
        Object end = normalizeValue(rule.getValueEnd(), rule.getField());

        if (start == null || end == null) return cb.conjunction();

        if (start instanceof LocalDateTime && end instanceof LocalDateTime) {
            return cb.between(field.as(LocalDateTime.class), (LocalDateTime) start, (LocalDateTime) end);
        }

        if (!(start instanceof Number) || !(end instanceof Number)) {
            return cb.conjunction();
        }

        return cb.between(field.as(Double.class), ((Number) start).doubleValue(), ((Number) end).doubleValue());
    }

    private Predicate buildIsEmpty(Rule rule, CriteriaQuery<?> query, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        if (isArrayField(rule.getField())) {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<BookEntity> subRoot = subquery.from(BookEntity.class);

            if (rule.getField() == RuleField.SHELF) {
                subRoot.join("shelves", JoinType.INNER);
            } else {
                Join<Object, Object> metadataJoin = subRoot.join("metadata", JoinType.INNER);
                joinArrayField(rule.getField(), metadataJoin);
            }

            subquery.select(cb.literal(1L)).where(cb.equal(subRoot.get("id"), root.get("id")));

            return cb.not(cb.exists(subquery));
        }

        Expression<?> field = getFieldExpression(rule.getField(), cb, root, progressJoin);
        if (field == null) return cb.conjunction();

        return cb.or(cb.isNull(field), cb.equal(cb.trim(field.as(String.class)), ""));
    }

    private Predicate buildIncludesAny(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, false);
        }

        return buildFieldInPredicate(rule.getField(), field -> field, ruleList, cb, progressJoin);
    }

    private Predicate buildExcludesAll(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return cb.not(buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, false));
        }

        return cb.not(buildFieldInPredicate(rule.getField(), field -> field, ruleList, cb, progressJoin));
    }

    private Predicate buildIncludesAll(Rule rule, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        List<String> ruleList = toStringList(rule.getValue());

        if (isArrayField(rule.getField())) {
            return buildArrayFieldPredicate(rule.getField(), ruleList, cb, root, true);
        }

        return buildFieldInPredicate(rule.getField(), field -> field, ruleList, cb, progressJoin);
    }

    private Predicate buildFieldInPredicate(RuleField ruleField,
                                            java.util.function.Function<Expression<?>, Expression<?>> fieldTransformer,
                                            List<String> ruleList,
                                            CriteriaBuilder cb,
                                            Join<BookEntity, UserBookProgressEntity> progressJoin) {
        Expression<?> field = fieldTransformer.apply(getFieldExpression(ruleField, cb, null, progressJoin));
        if (field == null) return cb.conjunction();

        if (ruleField == RuleField.READ_STATUS) {
            boolean hasUnset = ruleList.stream().anyMatch("UNSET"::equals);
            List<String> nonUnsetValues = ruleList.stream()
                    .filter(v -> !"UNSET".equals(v))
                    .collect(Collectors.toList());

            if (hasUnset && !nonUnsetValues.isEmpty()) {
                return cb.or(
                        cb.isNull(field),
                        field.as(String.class).in(nonUnsetValues)
                );
            } else if (hasUnset) {
                return cb.isNull(field);
            } else {
                return field.as(String.class).in(nonUnsetValues);
            }
        }

        List<String> lowerList = ruleList.stream().map(String::toLowerCase).collect(Collectors.toList());
        return cb.lower(field.as(String.class)).in(lowerList);
    }

    private Expression<?> getFieldExpression(RuleField field, CriteriaBuilder cb, Root<BookEntity> root, Join<BookEntity, UserBookProgressEntity> progressJoin) {
        return switch (field) {
            case LIBRARY -> root.get("library").get("id");
            case SHELF -> null;
            case READ_STATUS -> progressJoin.get("readStatus");
            case DATE_FINISHED -> progressJoin.get("dateFinished");
            case LAST_READ_TIME -> progressJoin.get("lastReadTime");
            case PERSONAL_RATING -> progressJoin.get("personalRating");
            case FILE_SIZE -> root.get("fileSizeKb");
            case METADATA_SCORE -> root.get("metadataMatchScore");
            case TITLE -> root.get("metadata").get("title");
            case SUBTITLE -> root.get("metadata").get("subtitle");
            case PUBLISHER -> root.get("metadata").get("publisher");
            case PUBLISHED_DATE -> root.get("metadata").get("publishedDate");
            case PAGE_COUNT -> root.get("metadata").get("pageCount");
            case LANGUAGE -> root.get("metadata").get("language");
            case SERIES_NAME -> root.get("metadata").get("seriesName");
            case SERIES_NUMBER -> root.get("metadata").get("seriesNumber");
            case SERIES_TOTAL -> root.get("metadata").get("seriesTotal");
            case ISBN13 -> root.get("metadata").get("isbn13");
            case ISBN10 -> root.get("metadata").get("isbn10");
            case AMAZON_RATING -> root.get("metadata").get("amazonRating");
            case AMAZON_REVIEW_COUNT -> root.get("metadata").get("amazonReviewCount");
            case GOODREADS_RATING -> root.get("metadata").get("goodreadsRating");
            case GOODREADS_REVIEW_COUNT -> root.get("metadata").get("goodreadsReviewCount");
            case HARDCOVER_RATING -> root.get("metadata").get("hardcoverRating");
            case HARDCOVER_REVIEW_COUNT -> root.get("metadata").get("hardcoverReviewCount");
            case RANOBEDB_RATING -> root.get("metadata").get("ranobedbRating");
            case AGE_RATING -> root.get("metadata").get("ageRating");
            case CONTENT_RATING -> root.get("metadata").get("contentRating");
            case ADDED_ON -> root.get("addedOn");
            case LUBIMYCZYTAC_RATING -> root.get("metadata").get("lubimyczytacRating");
            case DESCRIPTION -> root.get("metadata").get("description");
            case NARRATOR -> root.get("metadata").get("narrator");
            case AUDIBLE_RATING -> root.get("metadata").get("audibleRating");
            case AUDIBLE_REVIEW_COUNT -> root.get("metadata").get("audibleReviewCount");
            case ABRIDGED -> root.get("metadata").get("abridged");
            case AUDIOBOOK_DURATION -> root.join("bookFiles", JoinType.LEFT).get("durationSeconds");
            case IS_PHYSICAL -> root.get("isPhysical");
            case READING_PROGRESS -> {
                Expression<Float> koreader = cb.coalesce(progressJoin.get("koreaderProgressPercent"), 0f);
                Expression<Float> kobo = cb.coalesce(progressJoin.get("koboProgressPercent"), 0f);
                Expression<Float> pdf = cb.coalesce(progressJoin.get("pdfProgressPercent"), 0f);
                Expression<Float> epub = cb.coalesce(progressJoin.get("epubProgressPercent"), 0f);
                Expression<Float> cbx = cb.coalesce(progressJoin.get("cbxProgressPercent"), 0f);
                yield cb.function("GREATEST", Float.class, koreader, kobo, pdf, epub, cbx);
            }
            case FILE_TYPE -> cb.function("SUBSTRING_INDEX", String.class,
                    root.get("fileName"), cb.literal("."), cb.literal(-1));
            default -> null;
        };
    }

    private boolean isArrayField(RuleField field) {
        return field == RuleField.AUTHORS || field == RuleField.CATEGORIES ||
                field == RuleField.MOODS || field == RuleField.TAGS ||
                field == RuleField.GENRE || field == RuleField.SHELF;
    }

    private Join<?, ?> createArrayFieldJoin(RuleField field, Root<BookEntity> root) {
        if (field == RuleField.SHELF) {
            return root.join("shelves", JoinType.INNER);
        }
        Join<Object, Object> metadataJoin = root.join("metadata", JoinType.INNER);
        return joinArrayField(field, metadataJoin);
    }

    private Expression<String> getArrayFieldNameExpression(RuleField field, Join<?, ?> arrayJoin) {
        if (field == RuleField.SHELF) {
            return arrayJoin.get("id").as(String.class);
        }
        return arrayJoin.get("name");
    }

    private Join<?, ?> joinArrayField(RuleField field, Join<Object, Object> metadataJoin) {
        return switch (field) {
            case AUTHORS -> metadataJoin.join("authors", JoinType.INNER);
            case CATEGORIES -> metadataJoin.join("categories", JoinType.INNER);
            case MOODS -> metadataJoin.join("moods", JoinType.INNER);
            case TAGS -> metadataJoin.join("tags", JoinType.INNER);
            case GENRE -> metadataJoin.join("categories", JoinType.INNER);
            default -> throw new IllegalArgumentException("Not an array field: " + field);
        };
    }

    private Predicate buildArrayFieldPredicate(RuleField field, List<String> values, CriteriaBuilder cb, Root<BookEntity> root, boolean includesAll) {
        if (values.isEmpty()) {
            return cb.conjunction();
        }
        if (includesAll) {
            List<Predicate> predicates = values.stream()
                    .map(value -> {
                        Join<?, ?> arrayJoin = createArrayFieldJoin(field, root);
                        Expression<String> nameField = getArrayFieldNameExpression(field, arrayJoin);
                        return cb.equal(cb.lower(nameField), value.toLowerCase());
                    })
                    .toList();

            return cb.and(predicates.toArray(new Predicate[0]));
        } else {
            Join<?, ?> arrayJoin = createArrayFieldJoin(field, root);
            Expression<String> nameField = getArrayFieldNameExpression(field, arrayJoin);

            List<String> lowerValues = values.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());

            return cb.lower(nameField).in(lowerValues);
        }
    }

    private Object normalizeValue(Object value, RuleField field) {
        if (value == null) return null;

        if (field == RuleField.PUBLISHED_DATE) {
            return parseDate(value);
        }

        if (field == RuleField.DATE_FINISHED || field == RuleField.LAST_READ_TIME || field == RuleField.ADDED_ON) {
            LocalDateTime parsed = parseDate(value);
            if (parsed != null) {
                return parsed.atZone(ZoneId.systemDefault()).toInstant();
            }
            return null;
        }

        if (field == RuleField.READ_STATUS) {
            return value.toString();
        }

        if (field == RuleField.ABRIDGED || field == RuleField.IS_PHYSICAL) {
            return Boolean.valueOf(value.toString());
        }

        if (value instanceof Number) {
            return value;
        }

        return value.toString().toLowerCase();
    }

    private LocalDateTime parseDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime) return (LocalDateTime) value;

        try {
            return LocalDateTime.parse(value.toString(), DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDate.parse(value.toString()).atStartOfDay();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private List<String> toStringList(Object value) {
        if (value == null) return Collections.emptyList();
        if (value instanceof List) {
            return ((Collection<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        return Collections.singletonList(value.toString());
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
