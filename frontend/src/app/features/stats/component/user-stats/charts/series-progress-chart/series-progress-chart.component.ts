import {Component, computed, inject, input, linkedSignal, signal} from '@angular/core';
import {injectQuery, keepPreviousData} from '@tanstack/angular-query-experimental';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideChevronLeft, LucideChevronRight} from '@lucide/angular';
import {barX, defineChart, stack, text} from '@tanstack/charts';
import {decorative} from '@tanstack/charts/mark/decorative';
import {scaleLinear} from '@tanstack/charts/scales/linear';

import {AppButtonComponent} from '../../../../../../shared/ui/button/app-button.component';
import {AppRatingComponent} from '../../../../../../shared/ui/rating/app-rating.component';
import {AppInputComponent} from '../../../../../../shared/ui/input/app-input.component';
import {AppSelectComponent} from '../../../../../../shared/ui/select/app-select.component';
import {type SelectOption} from '../../../../../../shared/ui/select/app-select.options';
import {debouncedSignal} from '../../../../../../shared/util/debounced-signal';
import {SEARCH_DEBOUNCE_MS} from '../../../../../../shared/util/search-terms';
import {STATS_CARD, STATS_CONTEXT} from '../../../../card/stats-context';
import {StatsQueryService} from '../../../../data/stats-query.service';
import {
  type SeriesRow,
  type SeriesSort,
  type SeriesState,
  type SeriesStateFilter,
  type SeriesSummary,
} from '../../../../data/stats-response.models';
import {
  countTicks,
  shorten,
  STATS_CHART_VALUE,
  STATS_STATUS_COLORS,
  STATS_COMPACT_WIDTH,
  statsAxis,
  statsBar,
  statsBarBand,
  statsTooltip,
} from '../../../shared/stats-chart-style';
import {StatsChartPlotComponent} from '../../../shared/stats-chart-plot.component';
import {injectStatsText, STATS_CHART_HOST_CLASS} from '../../../shared/stats-chart-text';
import {type StatsInsight, StatsInsightListComponent} from '../../../shared/stats-insight-list.component';
import {type StatsLegendItem, StatsLegendComponent} from '../../../shared/stats-legend.component';

const SEGMENTS = [
  {id: 'read', color: STATS_STATUS_COLORS.READ},
  {id: 'reading', color: STATS_STATUS_COLORS.READING},
  {id: 'partiallyRead', color: STATS_STATUS_COLORS.PARTIALLY_READ},
  {id: 'paused', color: STATS_STATUS_COLORS.PAUSED},
  {id: 'abandoned', color: STATS_STATUS_COLORS.ABANDONED},
  {id: 'wontRead', color: STATS_STATUS_COLORS.WONT_READ},
  {id: 'unread', color: STATS_STATUS_COLORS.UNREAD},
] as const;
const STATE_COLORS = {
  completed: STATS_STATUS_COLORS.READ,
  'in-progress': STATS_STATUS_COLORS.READING,
  paused: STATS_STATUS_COLORS.PAUSED,
  abandoned: STATS_STATUS_COLORS.ABANDONED,
  'not-started': STATS_STATUS_COLORS.UNSET,
} satisfies Record<SeriesState, string>;
const FILTERS = [
  {value: 'all', labelKey: 'allStatus'},
  {value: 'in-progress', labelKey: 'inProgress'},
  {value: 'paused', labelKey: 'paused'},
  {value: 'not-started', labelKey: 'notStarted'},
  {value: 'completed', labelKey: 'completed'},
  {value: 'abandoned', labelKey: 'abandoned'},
] as const satisfies readonly {value: SeriesStateFilter; labelKey: string}[];
const SORTS = [
  {value: 'progress', labelKey: 'sortByProgress'},
  {value: 'rating', labelKey: 'sortByRating'},
  {value: 'books', labelKey: 'sortByBooks'},
  {value: 'name', labelKey: 'sortByName'},
] as const satisfies readonly {value: SeriesSort; labelKey: string}[];
const PAGE_SIZE = 10;

@Component({
  selector: 'app-series-progress-chart',
  imports: [
    AppButtonComponent,
    AppInputComponent,
    AppRatingComponent,
    AppSelectComponent,
    LucideChevronLeft,
    LucideChevronRight,
    StatsChartPlotComponent,
    StatsInsightListComponent,
    StatsLegendComponent,
    TranslocoPipe,
  ],
  template: `
    <app-stats-chart-plot [options]="chart()" />
    <app-stats-legend [items]="legend()" />

    @if (card.state() !== 'empty') {
      <section class="px-2">
        <div class="flex items-baseline justify-between gap-3">
          <h3 class="text-xs font-medium text-text-secondary">{{ text.t('seriesBreakdown') }}</h3>
          @if (tableQuery.data(); as result) {
            <span class="text-xs tabular-nums text-text-muted">
              {{ text.t('ofTotal', {filtered: text.number(result.matching), total: text.number(data().total)}) }}
            </span>
          }
        </div>

        <div class="mt-2 flex flex-wrap items-center gap-2">
          <div class="min-w-44 max-w-72 flex-1 @max-md:max-w-none">
            <app-input
              type="search"
              size="sm"
              [value]="searchTerm()"
              [placeholder]="text.t('searchSeries')"
              [ariaLabel]="text.t('searchSeries')"
              (valueChange)="searchTerm.set($event)" />
          </div>
          <div class="w-44 @max-md:w-full">
            <app-select
              size="sm"
              [options]="filterOptions()"
              [value]="filter()"
              [ariaLabel]="text.t('statusFilterLabel')"
              (valueChange)="filter.set($event ?? 'all')" />
          </div>
          <div class="w-44 @max-md:w-full">
            <app-select
              size="sm"
              [options]="sortOptions()"
              [value]="sort()"
              [ariaLabel]="text.t('sortLabel')"
              (valueChange)="sort.set($event ?? 'progress')" />
          </div>
        </div>

        <ul class="mt-2 divide-y divide-border">
          @if (tableQuery.isError()) {
            <li class="px-1 py-4 text-center text-sm text-text-secondary">{{ 'stats.card.loadError' | transloco }}</li>
          } @else if (card.state() === 'loading' || !tableQuery.data()) {
            @for (row of placeholderRows; track row) {
              <li class="py-2" aria-hidden="true">
                <span class="block h-5 w-1/3 animate-pulse rounded bg-text/8 motion-reduce:animate-none"></span>
              </li>
            }
          }
          @for (series of tableQuery.isError() ? [] : (tableQuery.data()?.rows ?? []); track series.series) {
            <li class="grid grid-cols-[minmax(0,1fr)_5rem_2.5rem] items-center gap-x-3 py-2 text-xs tabular-nums @xl:grid-cols-[minmax(0,1fr)_minmax(6rem,16rem)_5rem_2.5rem_5rem]">
              <div class="flex min-w-0 items-center gap-2">
                <span class="size-2.5 shrink-0 rounded-full" [style.background]="stateColors[series.state]" aria-hidden="true"></span>
                <span class="truncate text-sm font-medium text-text-strong" [title]="series.series">{{ series.series }}</span>
                @if (series.nextUnread) {
                  <span class="hidden min-w-0 flex-1 truncate text-text-muted @2xl:inline" [title]="text.t('next') + ' ' + series.nextUnread">
                    {{ text.t('next') }} <span class="text-text-secondary">{{ series.nextUnread }}</span>
                  </span>
                }
              </div>
              <div class="flex h-1.5 overflow-hidden rounded-full bg-text/8" aria-hidden="true">
                <div class="h-full" [style.width.%]="(series.completion ?? 0) * 100" [style.background]="stateColors.completed"></div>
                <div class="h-full" [style.width.%]="readingShare(series)" [style.background]="stateColors['in-progress']"></div>
              </div>
              <span class="hidden text-right text-text-muted @xl:block">
                {{ text.t('readOfBooks', {read: text.number(series.read), books: text.number(series.books)}) }}
              </span>
              <span class="text-right text-text-secondary">{{ series.completion === null ? '' : text.number(series.completion, {style: 'percent'}) }}</span>
              <span class="hidden justify-end @xl:flex" [title]="series.rating === null ? '' : ratingLabel(series.rating)">
                @if (series.rating !== null) {
                  <app-rating [value]="stars(series.rating)" readonly size="sm" [ariaLabel]="ratingLabel(series.rating)" />
                }
              </span>
            </li>
          } @empty {
            @if (tableQuery.data() && !tableQuery.isError()) {
              <li class="px-1 py-4 text-center text-sm text-text-secondary">
                {{ searchTerm() ? text.t('noMatchingSearch', {term: searchTerm()}) : ('stats.card.noData' | transloco) }}
              </li>
            }
          }
        </ul>

        <nav class="mt-3 flex items-center justify-center gap-3 text-xs tabular-nums text-text-secondary">
          <app-button variant="ghost" size="sm" iconOnly [ariaLabel]="'common.back' | transloco" [disabled]="page() === 0" (clicked)="page.set(page() - 1)">
            <svg lucideChevronLeft aria-hidden="true"></svg>
          </app-button>
          <span>{{ text.t('pageInfo', {current: page() + 1, total: totalPages()}) }}</span>
          <app-button variant="ghost" size="sm" iconOnly [ariaLabel]="'common.next' | transloco" [disabled]="page() + 1 >= totalPages()" (clicked)="page.set(page() + 1)">
            <svg lucideChevronRight aria-hidden="true"></svg>
          </app-button>
        </nav>
      </section>
    }

    <app-stats-insight-list [items]="insights()" />
  `,
  host: {class: STATS_CHART_HOST_CLASS},
})
export class SeriesProgressChartComponent {
  protected readonly card = inject(STATS_CARD);
  private readonly stats = inject(StatsQueryService);
  private readonly context = inject(STATS_CONTEXT);

  protected readonly text = injectStatsText('statsUser.seriesProgress');
  protected readonly placeholderRows = Array.from({length: PAGE_SIZE}, (_, index) => index);
  protected readonly stateColors = STATE_COLORS;

  readonly data = input.required<SeriesSummary>();

  readonly searchTerm = signal('');
  readonly filter = signal<SeriesStateFilter>('all');
  readonly sort = signal<SeriesSort>('progress');

  private readonly search = debouncedSignal(this.searchTerm, SEARCH_DEBOUNCE_MS);
  readonly page = linkedSignal({
    source: () => ({search: this.search(), state: this.filter(), sort: this.sort()}),
    computation: () => 0,
  });
  protected readonly tableQuery = injectQuery(() => ({
    ...this.stats.series(
      this.context.scope(),
      {search: this.search(), state: this.filter(), sort: this.sort(), page: this.page()},
    ).options,
    placeholderData: keepPreviousData,
  }));
  readonly totalPages = computed(() => Math.max(this.page() + 1, Math.ceil((this.tableQuery.data()?.matching ?? 0) / PAGE_SIZE)));

  readonly legend = computed<readonly StatsLegendItem[]>(() =>
    SEGMENTS.filter((segment) => this.data().top.some((series) => series[segment.id] > 0)).map((segment) => ({
      label: this.text.t(segment.id),
      color: segment.color,
    })),
  );

  readonly chart = computed(() => {
    const {t, number, stars} = this.text;
    const shown = this.data().top;
    const segments = shown.flatMap((series) =>
      SEGMENTS.map((segment) => ({series, name: series.series, segment: segment.id, books: series[segment.id]})),
    );

    return {
      ariaLabel: t('chartLabel'),
      definition: defineChart({
        chart: ({width}) => {
          const worded = width >= STATS_COMPACT_WIDTH;
          return {
            marks: [
              barX(segments.filter(({books}) => books > 0), {
                x: 'books',
                y: 'name',
                z: 'segment',
                layout: stack({order: SEGMENTS.map(({id}) => id)}),
                ...statsBar(width, true),
              }),
              decorative(text(shown, {
                x: 'books',
                y: 'series',
                text: ({read, books}) =>
                  worded ? t('readOfBooks', {read: number(read), books: number(books)}) : `${number(read)}/${number(books)}`,
                dx: 8,
                anchor: 'start',
                ...STATS_CHART_VALUE,
              })),
            ],
            scales: {
              x: {
                scale: scaleLinear,
                nice: true,
                grid: true,
                axis: statsAxis(t('axisNumberOfBooks'), countTicks(Math.max(0, ...shown.map(({books}) => books)), number)),
              },
              y: {
                scale: statsBarBand(shown.map(({series}) => series)),
                axis: statsAxis(t('axisSeries'), {format: (name: string) => shorten(name, 25)}),
              },
            },
            color: {domain: SEGMENTS.map(({id}) => id), range: SEGMENTS.map(({color}) => color)},
          };
        },
        focus: 'group-y',
        focusRing: false,
        maxFocusDistance: Number.POSITIVE_INFINITY,
        svgAnimation: true,
        tooltip: statsTooltip<(typeof segments)[number]>((points) => {
          const series = points[0].datum.series;
          return {
            title: series.series,
            rows: [
              ...points.map(({datum: row, color}) => ({label: t(row.segment), value: number(row.books), color})),
              ...(series.rating ? [{label: t('personalRating'), value: stars(series.rating / 2, {minimumFractionDigits: 1, maximumFractionDigits: 1})}] : []),
            ],
          };
        }),
      }),
    };
  });

  readonly insights = computed<readonly StatsInsight[]>(() => {
    const {t, number} = this.text;
    const {averageCompletion, measured, completed, inProgress, notStarted} = this.data();
    return [
      ...(averageCompletion === null
        ? []
        : [{
            label: t('averageProgress'),
            value: number(averageCompletion, {style: 'percent'}),
            detail: t('acrossSeries', {count: number(measured)}),
          }]),
      {label: t('completed'), value: number(completed)},
      {label: t('inProgress'), value: number(inProgress)},
      {label: t('notStarted'), value: number(notStarted)},
    ];
  });

  readonly filterOptions = computed<readonly SelectOption<SeriesStateFilter>[]>(() =>
    FILTERS.map(({value, labelKey}) => ({value, label: this.text.t(labelKey)})),
  );
  readonly sortOptions = computed<readonly SelectOption<SeriesSort>[]>(() =>
    SORTS.map(({value, labelKey}) => ({value, label: this.text.t(labelKey)})),
  );

  protected readingShare(series: SeriesRow): number {
    return series.intended > 0 ? (series.reading / series.intended) * 100 : 0;
  }

  protected stars(rating: number): number {
    return Math.round(rating) / 2;
  }

  protected ratingLabel(rating: number): string {
    return this.text.t('personalRatingValue', {rating: this.text.stars(rating / 2, {minimumFractionDigits: 1, maximumFractionDigits: 1})});
  }
}
