package org.booklore.stats;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import org.booklore.BookloreApplication;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookMetadataEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.stats.StatsResponses.Count;
import org.booklore.stats.StatsResponses.DayCount;
import org.booklore.stats.StatsResponses.Distribution;
import org.booklore.stats.StatsResponses.FinishCount;
import org.booklore.stats.StatsResponses.FinishTimes;
import org.booklore.stats.StatsResponses.FinishedBook;
import org.booklore.stats.StatsResponses.SeriesBar;
import org.booklore.stats.StatsResponses.SeriesPage;
import org.booklore.stats.StatsResponses.SeriesRow;
import org.booklore.stats.StatsResponses.SeriesSummary;
import org.booklore.stats.StatsResponses.Slot;
import org.booklore.stats.StatsResponses.Streaks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.booklore.stats.StatsFixture.NO_FILTER;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookloreApplication.class)
@Transactional
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:statstest;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE;MODE=MYSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.path-config=build/tmp/test-config",
        "app.bookdrop-folder=build/tmp/test-bookdrop"
})
class StatsTest {

    @Autowired
    private StatsService statsService;
    @Autowired
    private LibraryStatsController libraryController;
    @Autowired
    private ReadingStatsController readingController;
    @MockitoBean
    private AuthenticationService authenticationService;
    @PersistenceContext
    private EntityManager em;

    private StatsFixture seed;

    @BeforeEach
    void seed() {
        seed = new StatsFixture(em);
        signIn(seed.admin());
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aReaderSeesOnlyTheBooksInTheirLibraries() {
        seed.progress(seed.book("Dune", m -> {}), p -> p.readStatus(ReadStatus.READ));
        seed.progress(seed.book(seed.otherLibrary, "Watchmen", m -> {}), p -> p.readStatus(ReadStatus.READING));
        seed.flush();

        signIn(seed.member(seed.mainLibrary));
        assertThat(statsService.distribution(NO_FILTER, "status").rows()).containsExactly(new Count(ReadStatus.READ, 1));
        signIn(seed.member(seed.mainLibrary, seed.otherLibrary));
        assertThat(statsService.distribution(NO_FILTER, "status").rows())
                .containsExactlyInAnyOrder(new Count(ReadStatus.READ, 1), new Count(ReadStatus.READING, 1));
    }

    @Test
    void anotherReadersProgressAndSessionsDoNotCount() {
        BookEntity book = seed.book("Dune", m -> {});
        seed.progress(book, p -> p.readStatus(ReadStatus.READING));
        seed.progress(book, seed.otherReader, p -> p.readStatus(ReadStatus.READ));
        seed.session(book, "2026-03-03T10:00:00Z", "2026-03-03T11:00:00Z");
        seed.session(book, seed.otherReader, "2026-03-03T14:00:00Z", "2026-03-03T15:00:00Z");
        seed.flush();

        assertThat(statsService.distribution(NO_FILTER, "status").rows()).containsExactly(new Count(ReadStatus.READING, 1));
        assertThat(statsService.slots(NO_FILTER, "all").rows()).containsExactly(new Slot(2, 10, 3600));
    }

    @Test
    void aBookCountsOnceUnderEachFormatItHasWithPhysicalAsOneOfThem() {
        BookEntity book = seed.book("Dune", m -> {});
        seed.file(book, BookFileType.PDF, "dune.pdf");
        seed.file(book, BookFileType.EPUB, "dune.epub");
        seed.file(book, BookFileType.EPUB, "dune-illustrated.epub");
        BookEntity paperback = seed.book("Emma", m -> {});
        paperback.setIsPhysical(true);
        seed.file(paperback, BookFileType.MOBI, "emma.mobi");
        seed.book("Persuasion", m -> {}).setIsPhysical(true);
        seed.flush();

        assertThat(statsService.distribution(NO_FILTER, "format").rows())
                .containsExactly(new Count("PHYSICAL", 2), new Count("EPUB", 1), new Count("MOBI", 1), new Count("PDF", 1));
    }

    @Test
    void sessionsBelongToTheirLocalStartAcrossDaylightSavingChanges() {
        BookEntity book = seed.book("Book", m -> {});
        seed.session(book, "2026-03-28T23:30:00Z", "2026-03-28T23:45:00Z");
        seed.session(book, "2026-03-29T23:30:00Z", "2026-03-29T23:45:00Z");
        seed.session(book, "2026-03-14T11:30:00Z", "2026-03-14T12:00:00Z");
        seed.session(book, "2026-02-28T11:30:00Z", "2026-02-28T12:00:00Z");
        seed.flush();
        StatsQuery london = StatsFixture.zone("Europe/London");
        StatsQuery auckland = StatsFixture.zone("Pacific/Auckland");

        assertThat(statsService.slots(london, "2026-03").rows())
                .containsExactlyInAnyOrder(new Slot(6, 23, 900), new Slot(1, 0, 900), new Slot(6, 11, 1800));
        assertThat(statsService.days(london, "2026").rows()).containsExactly(
                new DayCount("2026-02-28", 1), new DayCount("2026-03-14", 1), new DayCount("2026-03-28", 1), new DayCount("2026-03-30", 1));
        assertThat(statsService.days(auckland, "2026").rows()).containsExactly(
                new DayCount("2026-03-01", 1), new DayCount("2026-03-15", 1), new DayCount("2026-03-29", 1), new DayCount("2026-03-30", 1));
        assertThat(statsService.slots(auckland, "2026-03").rows()).containsExactlyInAnyOrder(
                new Slot(7, 0, 3600), new Slot(7, 12, 900), new Slot(1, 12, 900));
    }

    @Test
    void streaksCountRunsOfLocalDays() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String year = String.valueOf(today.getYear());
        assertThat(statsService.days(NO_FILTER, year).streaks()).isEqualTo(new Streaks(0, 0, 0));

        BookEntity book = seed.book("Book", m -> {});
        for (int daysAgo : List.of(13, 12, 12, 11, 2, 1)) {
            Instant start = today.minusDays(daysAgo).atTime(10, 0).toInstant(ZoneOffset.UTC);
            seed.session(book, start.toString(), start.plusSeconds(600).toString());
        }
        seed.flush();

        assertThat(statsService.days(NO_FILTER, year).streaks()).isEqualTo(new Streaks(2, 3, 5));
    }

    @Test
    void finishTimesCountLocalDaysFromTheFirstSessionAndNameEachBandsFiveQuickest() {
        List<FinishedBook> quick = new ArrayList<>();
        for (int day = 0; day < 6; day++) {
            finished("Day " + day, "2026-03-01T12:00:00Z", "2026-03-0" + (day + 1) + "T20:00:00Z");
            quick.add(new FinishedBook("Day " + day, day));
        }
        finished("Across midnight", "2026-02-01T23:30:00Z", "2026-02-08T00:30:00Z");
        finished("Long", "2026-01-01T12:00:00Z", "2026-04-11T12:00:00Z");
        finished("Last year", "2025-12-01T12:00:00Z", "2025-12-10T12:00:00Z");
        seed.progress(seed.book("No sessions", m -> {}), p -> p.dateFinished(Instant.parse("2026-03-01T12:00:00Z")));
        seed.flush();

        assertThat(statsService.daysToFinish(NO_FILTER, "2026")).isEqualTo(new FinishTimes(List.of(
                new FinishCount("under-a-week", 6, quick.subList(0, 5)),
                new FinishCount("1-to-2-weeks", 1, List.of(new FinishedBook("Across midnight", 7))),
                new FinishCount("3-months-or-more", 1, List.of(new FinishedBook("Long", 100)))),
                3.5, quick.getFirst(), new FinishedBook("Long", 100)));
        assertThat(statsService.daysToFinish(StatsFixture.zone("Pacific/Auckland"), "2026").rows())
                .extracting(FinishCount::band, FinishCount::books)
                .containsExactly(tuple("under-a-week", 7L), tuple("3-months-or-more", 1L));
    }

    @Test
    void progressIsTheFurthestTheReaderGotAndOnlyAFinishedBookReachesAHundred() {
        seed.book("Unopened", m -> {});
        seed.progress(seed.book("Barely started", m -> {}), p -> p.pdfProgressPercent(0.3f));
        seed.progress(seed.book("A quarter", m -> {}), p -> p.epubProgressPercent(25.7f));
        seed.progress(seed.book("Nearly", m -> {}), p -> p.koboProgressPercent(99.5f));
        seed.progress(seed.book("On KOReader", m -> {}), p -> p.koreaderProgressPercent(0.5f));
        seed.progress(seed.book("Marked read", m -> {}), p -> p.readStatus(ReadStatus.READ).epubProgressPercent(40f));
        seed.progress(seed.book("Reading", m -> {}), p -> p.readStatus(ReadStatus.READING).epubProgressPercent(60f));
        seed.progress(seed.book("Rereading", m -> {}), p -> p.readStatus(ReadStatus.RE_READING).epubProgressPercent(20f));
        BookEntity inAFile = seed.book("Further in a file", m -> {});
        seed.progress(inAFile, p -> p.epubProgressPercent(10f));
        BookFileEntity file = seed.file(inAFile, BookFileType.EPUB, "further.epub");
        em.persist(UserBookFileProgressEntity.builder().user(seed.reader).bookFile(file).progressPercent(80f).build());
        em.persist(UserBookFileProgressEntity.builder().user(seed.otherReader).bookFile(file).progressPercent(95f).build());
        seed.flush();

        assertThat(statsService.distribution(NO_FILTER, "stopped").rows()).containsExactly(
                new Count(0, 1), new Count(1, 1), new Count(25, 1), new Count(50, 1), new Count(80, 1), new Count(99, 1), new Count(100, 1));
    }

    @Test
    void eachBookIsReadIfItWasEverFinishedAndCompletionLeavesOutBooksGivenUp() {
        seed.progress(seed.book("One", m -> series(m, "Saga", 1f)), p -> p.readStatus(ReadStatus.READ).personalRating(8));
        seed.progress(seed.book("Two", m -> series(m, "Saga", 2f)), p -> p.readStatus(ReadStatus.PARTIALLY_READ));
        seed.book("Three", m -> series(m, "Saga", 3f));
        seed.progress(seed.book("Four", m -> series(m, "Saga", 4f)),
                p -> p.readStatus(ReadStatus.RE_READING).dateFinished(Instant.parse("2026-01-01T00:00:00Z")).personalRating(6));
        seed.progress(seed.book("Five", m -> series(m, "Saga", 5f)), p -> p.readStatus(ReadStatus.ABANDONED));
        seed.progress(seed.book("Alone", m -> series(m, "Annals", null)), p -> p.readStatus(ReadStatus.UNREAD));
        seed.book("Loose", m -> {});
        seed.flush();

        SeriesSummary summary = statsService.seriesSummary(NO_FILTER);

        assertThat(summary.top()).containsExactly(
                new SeriesBar("Saga", 5, 2, 0, 1, 0, 1, 0, 1, 7.0),
                new SeriesBar("Annals", 1, 0, 0, 0, 0, 0, 0, 1, null));
        assertThat(summary.total()).isEqualTo(2);
        assertThat(summary.averageCompletion()).isEqualTo(0.25);
        assertThat(List.of(summary.completed(), summary.inProgress(), summary.notStarted())).containsExactly(0L, 1L, 1L);
        assertThat(statsService.series(NO_FILTER, null, null, "progress", 0)).isEqualTo(new SeriesPage(List.of(
                new SeriesRow("Saga", "in-progress", 5, 2, 0, 4, 0.5, 7.0, "Two"),
                new SeriesRow("Annals", "not-started", 1, 0, 0, 1, 0.0, null, "Alone")), 2));
    }

    @Test
    void eachStatsPermissionOpensOnlyItsOwnPage() {
        seed.book("Dune", m -> {});
        seed.flush();

        signInWith(permissions -> permissions.setCanAccessLibraryStats(true));
        assertThat(libraryController.getLibraryDistribution(NO_FILTER, "format").getBody())
                .isEqualTo(new Distribution(List.of(new Count(null, 1)), null, null));
        assertThatThrownBy(() -> libraryController.getLibraryDistribution(NO_FILTER, "status")).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> readingController.getReadingDistribution(NO_FILTER, "status")).isInstanceOf(AccessDeniedException.class);

        signInWith(permissions -> permissions.setCanAccessUserStats(true));
        assertThat(readingController.getReadingDistribution(NO_FILTER, "status").getBody())
                .isEqualTo(new Distribution(List.of(new Count(ReadStatus.UNSET, 1)), null, null));
        assertThatThrownBy(() -> libraryController.getLibraryDistribution(NO_FILTER, "format")).isInstanceOf(AccessDeniedException.class);
    }

    private void finished(String title, String firstSession, String finishedAt) {
        BookEntity book = seed.book(title, m -> {});
        seed.session(book, firstSession, Instant.parse(firstSession).plusSeconds(600).toString());
        seed.progress(book, p -> p.dateFinished(Instant.parse(finishedAt)));
    }

    private static void series(BookMetadataEntity metadata, String name, Float number) {
        metadata.setSeriesName(name);
        metadata.setSeriesNumber(number);
    }

    private void signInWith(Consumer<BookLoreUser.UserPermissions> grant) {
        BookLoreUser user = seed.member(seed.mainLibrary);
        grant.accept(user.getPermissions());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        signIn(user);
    }

    private void signIn(BookLoreUser user) {
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }
}
