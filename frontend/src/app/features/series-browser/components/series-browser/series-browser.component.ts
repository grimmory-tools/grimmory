import {Component, DestroyRef, ElementRef, HostListener, computed, effect, inject, OnInit, signal, viewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ProgressSpinner} from '@openng/optimus-ui/progressspinner';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Select} from '@openng/optimus-ui/select';
import {Popover} from '@openng/optimus-ui/popover';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';
import {injectInfiniteQuery} from '@tanstack/angular-query';
import {mapAppSeriesSummary, SeriesSummary} from '../../model/series.model';
import {SeriesCardComponent} from '../series-card/series-card.component';
import {SeriesQueryService} from '../../service/series-query.service';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {ActivatedRoute, Router} from '@angular/router';
import {createVirtualGrid} from '../../../../shared/util/virtual-grid.util';
import {RouteScrollPositionService} from '../../../../shared/service/route-scroll-position.service';
import {GridDensityButtonsComponent, type GridDensityDirection} from '../../../../shared/components/grid-density-buttons/grid-density-buttons.component';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {ScalePreference} from '../../../../shared/util/scale-preference.util';
import {LayoutService} from '../../../../shared/layout/layout.service';
import {createGridDensity} from '../../../../shared/util/grid-density.util';

interface FilterOption {
  label: string;
  value: string;
}

interface SortOption {
  label: string;
  value: string;
}

@Component({
  selector: 'app-series-browser',
  standalone: true,
  templateUrl: './series-browser.component.html',
  styleUrls: ['./series-browser.component.scss'],
  imports: [
    FormsModule,
    ProgressSpinner,
    InputText,
    Select,
    Popover,
    TranslocoDirective,
    TranslocoPipe,
    GridDensityButtonsComponent,
    SeriesCardComponent,
  ]
})
export class SeriesBrowserComponent implements OnInit {

  private static readonly BASE_WIDTH = 230;
  private static readonly BASE_HEIGHT = 285;
  private static readonly MOBILE_BASE_WIDTH = 180;
  private static readonly MOBILE_BASE_HEIGHT = 250;
  private static readonly GRID_GAP = 20;
  private static readonly DEFAULT_MOBILE_GRID_COLUMNS = 2;
  private static readonly MIN_MOBILE_GRID_COLUMNS = 2;
  private static readonly MAX_MOBILE_GRID_COLUMNS = 3;
  private static readonly SCALE_STORAGE_KEY = 'seriesScalePreference';
  private static readonly MOBILE_COLUMNS_STORAGE_KEY = 'seriesMobileColumnsPreference';
  private static readonly MIN_SCALE = 0.7;
  private static readonly MAX_SCALE = 1.3;

  private readonly seriesQueryService = inject(SeriesQueryService);
  private readonly pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly scrollService = inject(RouteScrollPositionService);
  private readonly localStorageService = inject(LocalStorageService);
  private readonly layoutService = inject(LayoutService);

  private readonly searchTerm = signal('');
  private readonly statusFilter = signal('all');
  private readonly sortBy = signal('name-asc');
  private readonly seriesQuery = injectInfiniteQuery(() => this.seriesQueryService.infinitePage({
    search: this.searchTerm(),
    status: this.statusFilter(),
    sort: this.sortBy(),
  }));
  readonly isBooksLoading = computed(() => this.seriesQuery.isPending());
  readonly filteredSeries = computed(() =>
    (this.seriesQuery.data()?.pages.flatMap(page => page.content) ?? []).map(mapAppSeriesSummary)
  );

  private readonly scrollElement = viewChild<ElementRef<HTMLElement>>('scrollElement');
  private readonly initialScrollOffset = () => this.scrollService.getPosition(this.scrollService.keyFor(this.activatedRoute)) ?? 0;
  private readonly scalePreference = new ScalePreference(this.localStorageService, {
    storageKey: SeriesBrowserComponent.SCALE_STORAGE_KEY,
    minScale: SeriesBrowserComponent.MIN_SCALE,
    maxScale: SeriesBrowserComponent.MAX_SCALE,
  });
  private readonly scaleFactor = this.scalePreference.scaleFactor;
  readonly screenWidth = signal(typeof window !== 'undefined' ? window.innerWidth : 1024);
  readonly isMobile = computed(() => !this.layoutService.isDesktop());
  private readonly baseCardWidth = computed(() => this.isMobile()
    ? SeriesBrowserComponent.MOBILE_BASE_WIDTH
    : SeriesBrowserComponent.BASE_WIDTH
  );
  private readonly gridDensity = createGridDensity(this.localStorageService, {
    useFixedColumns: this.isMobile,
    screenWidth: this.screenWidth,
    storageKey: SeriesBrowserComponent.MOBILE_COLUMNS_STORAGE_KEY,
    defaultColumns: SeriesBrowserComponent.DEFAULT_MOBILE_GRID_COLUMNS,
    minColumns: SeriesBrowserComponent.MIN_MOBILE_GRID_COLUMNS,
    maxColumns: SeriesBrowserComponent.MAX_MOBILE_GRID_COLUMNS,
    scale: this.scaleFactor,
    minScale: SeriesBrowserComponent.MIN_SCALE,
    maxScale: SeriesBrowserComponent.MAX_SCALE,
    gap: SeriesBrowserComponent.GRID_GAP,
    baseWidth: this.baseCardWidth,
    setScale: scale => this.scalePreference.setScale(scale),
  });
  readonly gridDensitySmallerDisabled = this.gridDensity.smallerDisabled;
  readonly gridDensityLargerDisabled = this.gridDensity.largerDisabled;
  filterOptions: FilterOption[] = [];
  sortOptions: SortOption[] = [];

  @HostListener('window:resize')
  onResize(): void {
    this.screenWidth.set(window.innerWidth);
  }

  private readonly cardAspectRatio = computed(() => {
    const baseHeight = this.isMobile()
      ? SeriesBrowserComponent.MOBILE_BASE_HEIGHT
      : SeriesBrowserComponent.BASE_HEIGHT;
    return baseHeight / this.baseCardWidth();
  });
  private readonly minCardWidth = computed(() => this.isMobile()
    ? 1
    : Math.round(this.baseCardWidth() * this.scaleFactor())
  );
  readonly virtualGrid = createVirtualGrid({
    items: this.filteredSeries,
    scrollElement: this.scrollElement,
    minItemWidth: this.minCardWidth,
    gap: this.gridDensity.gap,
    columns: this.gridDensity.columns,
    initialOffset: this.initialScrollOffset,
    fillItemWidth: true,
    estimateItemHeight: itemWidth => Math.round(itemWidth * this.cardAspectRatio()),
  });
  readonly currentCardScale = computed(() => this.virtualGrid.itemWidth() / this.baseCardWidth());

  constructor() {
    effect(() => {
      const lastVisible = this.virtualGrid.virtualizer.getVirtualItems().at(-1);
      const itemCount = this.filteredSeries().length;
      if (lastVisible
          && lastVisible.index >= itemCount - 12
          && this.seriesQuery.hasNextPage()
          && !this.seriesQuery.isFetching()) {
        void this.seriesQuery.fetchNextPage({cancelRefetch: false});
      }
    });
  }

  get searchValue(): string {
    return this.searchTerm();
  }

  get statusFilterValue(): string {
    return this.statusFilter();
  }

  get sortByValue(): string {
    return this.sortBy();
  }

  ngOnInit(): void {
    this.pageTitle.setPageTitle(this.t.translate('seriesBrowser.pageTitle'));
    this.scrollService.trackRoute({
      scrollElement: this.scrollElement,
      route: this.activatedRoute,
      destroyRef: this.destroyRef,
    });

    this.filterOptions = [
      {label: this.t.translate('seriesBrowser.filters.all'), value: 'all'},
      {label: this.t.translate('seriesBrowser.filters.notStarted'), value: 'not-started'},
      {label: this.t.translate('seriesBrowser.filters.inProgress'), value: 'in-progress'},
      {label: this.t.translate('seriesBrowser.filters.completed'), value: 'completed'},
      {label: this.t.translate('seriesBrowser.filters.abandoned'), value: 'abandoned'}
    ];

    this.sortOptions = [
      {label: this.t.translate('seriesBrowser.sort.nameAsc'), value: 'name-asc'},
      {label: this.t.translate('seriesBrowser.sort.nameDesc'), value: 'name-desc'},
      {label: this.t.translate('seriesBrowser.sort.bookCount'), value: 'book-count'},
      {label: this.t.translate('seriesBrowser.sort.progress'), value: 'progress'},
      {label: this.t.translate('seriesBrowser.sort.recentlyRead'), value: 'recently-read'},
      {label: this.t.translate('seriesBrowser.sort.recentlyAdded'), value: 'recently-added'}
    ];
  }

  onSearchChange(value: string): void {
    this.searchTerm.set(value);
  }

  onStatusFilterChange(value: string): void {
    this.statusFilter.set(value);
  }

  onSortChange(value: string): void {
    this.sortBy.set(value);
  }

  adjustGridDensity(direction: GridDensityDirection): void {
    this.gridDensity.adjust(direction, this.virtualGrid);
  }

  navigateToSeries(series: SeriesSummary): void {
    this.router.navigate(['/series', series.seriesName]);
  }

}
