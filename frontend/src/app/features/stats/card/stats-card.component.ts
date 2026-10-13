import {NgComponentOutlet} from '@angular/common';
import {
  Component,
  computed,
  forwardRef,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {LucideChevronDown, LucideChevronLeft, LucideChevronRight, LucideChevronUp, LucideGripVertical, LucideX} from '@lucide/angular';
import {injectQuery, keepPreviousData} from '@tanstack/angular-query-experimental';
import {addDays, addWeeks, getISOWeek, getISOWeekYear} from 'date-fns';

import {createBrowseSkeletonDelay} from '../../../shared/browse/skeleton-delay';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {cn} from '../../../shared/ui/cn';
import {AppMessageComponent} from '../../../shared/ui/message/app-message.component';
import {AppSelectComponent} from '../../../shared/ui/select/app-select.component';
import {type SelectOption} from '../../../shared/ui/select/app-select.options';
import {isoWeekStart} from '../component/shared/stats-chart-data';
import {StatsQueryService} from '../data/stats-query.service';
import {
  STATS_ITEM_DIMENSIONS,
  STATS_ITEM_MEASURES,
  type StatsItemDimension,
  type StatsItemMeasure,
} from '../data/stats-response.models';
import {
  type AnyStatsChartDefinition,
  type StatsCardParams,
  type StatsChartSize,
  type StatsFilterKind,
  STATS_ITEM_NAMES,
} from './stats-chart-definition';
import {STATS_CARD, STATS_CONTEXT, type StatsCardContext, type StatsCardState} from './stats-context';

export interface StatsCardEditing {
  readonly canMoveEarlier: boolean;
  readonly canMoveLater: boolean;
}

interface StatsCardPeriod {
  readonly year: number | null;
  readonly month: number | null;
  readonly week: number;
  readonly weekday: number | null;
}

const YEAR_OPTION_COUNT = 11;

const PLOT_HEIGHTS = {
  small: 240,
  medium: 300,
  wide: 340,
  full: 380,
} as const satisfies Record<StatsChartSize, number>;

let nextHeadingId = 0;

@Component({
  selector: 'app-stats-card',
  imports: [
    AppButtonComponent,
    AppMessageComponent,
    AppSelectComponent,
    LucideChevronDown,
    LucideChevronLeft,
    LucideChevronRight,
    LucideChevronUp,
    LucideGripVertical,
    LucideX,
    NgComponentOutlet,
    TranslocoPipe,
  ],
  templateUrl: './stats-card.component.html',
  providers: [{provide: STATS_CARD, useExisting: forwardRef(() => StatsCardComponent)}],
  host: {
    class: '@container block h-full min-w-0',
    '[style.--stats-plot-height]': 'plotHeight() + "px"',
  },
})
export class StatsCardComponent implements StatsCardContext {
  private readonly stats = inject(StatsQueryService);
  private readonly context = inject(STATS_CONTEXT);
  private readonly transloco = inject(TranslocoService);

  readonly definition = input.required<AnyStatsChartDefinition>();
  readonly editing = input<StatsCardEditing | null>(null);
  readonly moved = output<-1 | 1>();
  readonly removed = output<void>();

  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });

  protected readonly headingId = `stats-card-heading-${nextHeadingId++}`;
  protected readonly period = linkedSignal({
    source: () => this.definition().filter,
    computation: defaultPeriod,
  });
  protected readonly item = signal<StatsItemDimension>('author');
  protected readonly measure = signal<StatsItemMeasure>('books');

  private readonly params = computed<StatsCardParams>(() => {
    const scope = this.context.scope();
    const {year, month, week, weekday} = this.period();
    const currentYear = new Date().getFullYear();

    switch (this.definition().filter) {
      case 'none':
        return scope;
      case 'year':
        return {...scope, year: year ?? currentYear};
      case 'yearMonth':
        return {...scope, year, month};
      case 'yearWeekday':
        return {...scope, year, weekday};
      case 'week':
        return {...scope, year: year ?? currentYear, week};
      case 'item':
        return {...scope, item: this.item(), by: this.measure()};
    }
  });

  private readonly request = computed(() => this.definition().query(this.params(), this.stats));

  private readonly query = injectQuery(() => ({
    ...this.request(),
    placeholderData: keepPreviousData,
  }));

  private readonly shownParams = linkedSignal<{params: StatsCardParams; settled: boolean}, StatsCardParams>({
    source: () => ({params: this.params(), settled: !this.query.isPlaceholderData()}),
    computation: (source, previous) => (source.settled || !previous ? source.params : previous.value),
  });

  private readonly chartData = computed(() => {
    const response = this.query.data() ?? this.request().meta?.['empty'];
    const {select} = this.definition();
    return select ? select(response, this.shownParams()) : response;
  });

  protected readonly chartInputs = computed(() => ({data: this.chartData()}));

  readonly state = computed<StatsCardState>(() => {
    if (this.query.isError()) return 'error';
    if (this.query.data() === undefined) return 'loading';
    return this.definition().isEmpty(this.chartData()) ? 'empty' : 'ready';
  });

  private readonly placeholdersShown = createBrowseSkeletonDelay(
    computed(() => (this.state() === 'loading' ? 'pending' : 'success')),
    signal(false),
  );
  protected readonly contentHidden = computed(() => this.state() === 'loading' && !this.placeholdersShown());

  protected readonly sectioned = computed(() => this.definition().filter !== 'none');
  protected readonly plotHeight = computed(() => PLOT_HEIGHTS[this.definition().size]);

  protected readonly headerClass = computed(() =>
    cn(
      'relative flex min-w-0 flex-wrap justify-between gap-3 px-4 pt-4 md:px-5 md:pt-5',
      this.sectioned() ? 'items-center pb-4 md:pb-5' : 'items-start',
    ),
  );
  protected readonly actionsClass = computed(() =>
    this.sectioned() || this.editing()
      ? cn('flex min-w-0 max-w-full flex-wrap items-center gap-2', !this.sectioned() && 'h-6 content-center')
      : 'hidden',
  );
  protected readonly contentClass = computed(() =>
    cn(
      'flex min-h-0 min-w-0 flex-1 flex-col justify-center px-2 pb-4 md:px-3 md:pb-5',
      this.sectioned() ? 'pt-4 md:pt-5' : 'pt-3 md:pt-4',
    ),
  );

  private readonly itemName = computed(() => {
    this.activeLang();
    return this.itemLabel(this.item());
  });
  protected readonly titleParams = computed(() =>
    this.definition().filter === 'item' ? {kind: this.itemName()} : {},
  );
  readonly description = computed(() => {
    this.activeLang();
    const key = this.definition().descriptionKey;
    return key && this.transloco.translate(key);
  });
  protected readonly yearOptions: readonly SelectOption<number>[] = Array.from({length: YEAR_OPTION_COUNT}, (_, index) => {
    const year = new Date().getFullYear() - index;
    return {value: year, label: year.toString()};
  });

  protected readonly monthOptions = computed<readonly SelectOption<number>[]>(() => {
    const formatter = new Intl.DateTimeFormat(this.activeLang(), {month: 'long'});
    return Array.from({length: 12}, (_, index) => ({
      value: index + 1,
      label: formatter.format(new Date(2000, index, 1)),
    }));
  });

  protected readonly weekdayOptions = computed<readonly SelectOption<number>[]>(() => {
    const formatter = new Intl.DateTimeFormat(this.activeLang(), {weekday: 'long', timeZone: 'UTC'});
    return Array.from({length: 7}, (_, index) => ({
      value: index + 1,
      label: formatter.format(Date.UTC(2024, 0, 1 + index)),
    }));
  });

  protected readonly itemOptions = computed<readonly SelectOption<StatsItemDimension>[]>(() => {
    this.activeLang();
    return STATS_ITEM_DIMENSIONS.map(item => ({value: item, label: this.itemLabel(item)}));
  });

  protected readonly measureOptions = computed<readonly SelectOption<StatsItemMeasure>[]>(() => {
    this.activeLang();
    return STATS_ITEM_MEASURES.map(measure => ({
      value: measure,
      label: this.transloco.translate(`stats.filter.measures.${measure}`),
    }));
  });

  protected readonly weekRangeLabel = computed(() => {
    const {year, week} = this.period();
    const start = isoWeekStart(year ?? getISOWeekYear(new Date()), week);
    const formatter = new Intl.DateTimeFormat(this.activeLang(), {month: 'short', day: 'numeric'});
    return `${formatter.format(start)} – ${formatter.format(addDays(start, 6))}`;
  });

  private itemLabel(item: StatsItemDimension): string {
    return this.transloco.translate(`stats.filter.dataTypes.${STATS_ITEM_NAMES[item]}`);
  }

  protected selectYear(year: number | null): void {
    const filter = this.definition().filter;
    if (year === null && filter !== 'yearMonth' && filter !== 'yearWeekday') return;

    this.period.update(period => (year === null ? {...period, year: null, month: null} : {...period, year}));
  }

  protected selectWeekday(weekday: number | null): void {
    this.period.update(period => ({...period, weekday}));
  }

  protected selectMonth(month: number | null): void {
    this.period.update(period => ({...period, month}));
  }

  protected stepWeek(delta: -1 | 1): void {
    this.period.update(period => {
      const year = period.year ?? getISOWeekYear(new Date());
      const shifted = addWeeks(isoWeekStart(year, period.week), delta);
      return {...period, year: getISOWeekYear(shifted), week: getISOWeek(shifted)};
    });
  }
}

function defaultPeriod(filter: StatsFilterKind): StatsCardPeriod {
  const now = new Date();
  return {
    year: filter === 'year' ? now.getFullYear() : filter === 'week' ? getISOWeekYear(now) : null,
    month: null,
    week: getISOWeek(now),
    weekday: null,
  };
}
