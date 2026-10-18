package org.booklore.service.stats;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.booklore.browse.FacetLogic;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.stats.StatsQuery;
import org.booklore.model.dto.stats.StatsResponses.ActivityMonth;
import org.booklore.model.dto.stats.StatsResponses.BookFlow;
import org.booklore.model.dto.stats.StatsResponses.ComparisonCell;
import org.booklore.model.dto.stats.StatsResponses.Days;
import org.booklore.model.dto.stats.StatsResponses.DecadeCell;
import org.booklore.model.dto.stats.StatsResponses.Distribution;
import org.booklore.model.dto.stats.StatsResponses.FinishTimes;
import org.booklore.model.dto.stats.StatsResponses.GenreTime;
import org.booklore.model.dto.stats.StatsResponses.LengthCell;
import org.booklore.model.dto.stats.StatsResponses.ProfileRow;
import org.booklore.model.dto.stats.StatsResponses.Rows;
import org.booklore.model.dto.stats.StatsResponses.SeriesPage;
import org.booklore.model.dto.stats.StatsResponses.SeriesSummary;
import org.booklore.model.dto.stats.StatsResponses.Slots;
import org.booklore.model.dto.stats.StatsResponses.TopName;
import org.booklore.model.dto.stats.StatsResponses.Week;
import org.booklore.model.entity.BookEntity;
import org.booklore.service.browse.BookFilterSpecifications;
import org.booklore.service.browse.BrowseParams;
import org.booklore.service.browse.BrowseScopeFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final AuthenticationService authenticationService;
    private final BrowseScopeFactory scopeFactory;
    private final BookFilterSpecifications filterSpecifications;
    private final EntityManager entityManager;

    public Distribution distribution(StatsQuery query, String dimension) {
        return new BookQueries(entityManager, scope(query)).distribution(BookQueries.Dimension.valueOf(dimension.toUpperCase(Locale.ROOT)));
    }

    public Rows<LengthCell> length(StatsQuery query) {
        return new BookQueries(entityManager, scope(query)).length();
    }

    public Rows<ComparisonCell> comparison(StatsQuery query) {
        return new BookQueries(entityManager, scope(query)).comparison();
    }

    public Rows<DecadeCell> decade(StatsQuery query) {
        return new BookQueries(entityManager, scope(query)).decade();
    }

    public BookFlow bookFlow(StatsQuery query) {
        return new BookQueries(entityManager, scope(query)).flow();
    }

    public Rows<ActivityMonth> activity(StatsQuery query) {
        return new BookQueries(entityManager, scope(query)).activity();
    }

    public Rows<TopName> top(StatsQuery query, String kind, String by) {
        return new BookQueries(entityManager, scope(query)).top(BookQueries.Kind.valueOf(kind.toUpperCase(Locale.ROOT)), BookQueries.Measure.valueOf(by.toUpperCase(Locale.ROOT)));
    }

    public Slots slots(StatsQuery query, String period) {
        Scope scope = scope(query);
        return new SessionQueries(entityManager, scope).slots(Period.parse(period, scope.zone()));
    }

    public Days days(StatsQuery query, String year) {
        Scope scope = scope(query);
        return new SessionQueries(entityManager, scope).days(Period.parseYear(year, scope.zone()));
    }

    public Rows<GenreTime> genres(StatsQuery query) {
        return new SessionQueries(entityManager, scope(query)).genres();
    }

    public Week week(StatsQuery query, String week) {
        Scope scope = scope(query);
        return new SessionQueries(entityManager, scope).week(Period.parseWeek(week, scope.zone()));
    }

    public FinishTimes daysToFinish(StatsQuery query, String year) {
        Scope scope = scope(query);
        return new SessionQueries(entityManager, scope).daysToFinish(Period.parseYear(year, scope.zone()));
    }

    public SeriesSummary seriesSummary(StatsQuery query) {
        return new SeriesQueries(entityManager, scope(query)).summary();
    }

    public SeriesPage series(StatsQuery query, String search, String state, String sort, int page) {
        return new SeriesQueries(entityManager, scope(query)).page(search, state, SeriesQueries.Sort.valueOf(sort.toUpperCase(Locale.ROOT)), page);
    }

    public Rows<ProfileRow> profile(StatsQuery query) {
        return new ReadingProfile(entityManager, scope(query)).profile();
    }

    private Scope scope(StatsQuery query) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Specification<BookEntity> matching = filterSpecifications.base(
                query.query(),
                BrowseParams.parseFacets(query.facet()),
                FacetLogic.from(query.facetLogic()),
                scopeFactory.from(user),
                null);
        return new Scope(user.getId(), matching, query.zone(), Instant.now());
    }
}
