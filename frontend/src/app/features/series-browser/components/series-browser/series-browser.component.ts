import {Component, DestroyRef, ElementRef, HostListener, computed, inject, OnInit, signal, viewChild} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ProgressSpinner} from 'primeng/progressspinner';
import {InputText} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {Popover} from 'primeng/popover';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {SeriesDataService} from '../../service/series-data.service';
import {SeriesSummary} from '../../model/series.model';
import {SeriesCardComponent} from '../series-card/series-card.component';
import {BookService} from '../../../book/service/book.service';
import {ReadStatus} from '../../../book/model/book.model';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {ActivatedRoute, Router} from '@angular/router';
import {createVirtualGrid, scaleForGridColumns} from '../../../../shared/util/virtual-grid.util';
import {RouteScrollPositionService} from '../../../../shared/service/route-scroll-position.service';
import {GridDensityButtonsComponent} from '../../../../shared/components/grid-density-buttons/grid-density-buttons.component';
import {LocalStorageService} from '../../../../shared/service/local-storage.service';
import {ScalePreference} from '../../../../shared/util/scale-preference.util';

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
  private static readonly SCALE_STORAGE_KEY = 'seriesScalePreference';
  private static readonly MIN_SCALE = 0.7;
  private static readonly MAX_SCALE = 1.3;

  private seriesDataService = inject(SeriesDataService);
  private bookService = inject(BookService);
  private pageTitle = inject(PageTitleService);
  private t = inject(TranslocoService);
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  private destroyRef = inject(DestroyRef);
  private scrollService = inject(RouteScrollPositionService);
  private localStorageService = inject(LocalStorageService);

  readonly isBooksLoading = this.bookService.isBooksLoading;
  private readonly searchTerm = signal('');
  private readonly statusFilter = signal('all');
  private readonly sortBy = signal('name-asc');
  readonly filteredSeries = computed(() => {
    let result = this.seriesDataService.allSeries();

    const search = this.searchTerm().trim().toLowerCase();
    if (search) {
      result = result.filter(series =>
        series.seriesName.toLowerCase().includes(search) ||
        series.authors.some(author => author.toLowerCase().includes(search))
      );
    }

    result = this.applyStatusFilter(result, this.statusFilter());
    return this.applySort(result, this.sortBy());
  });

  private readonly scrollElement = viewChild<ElementRef<HTMLElement>>('scrollElement');
  private readonly initialScrollOffset = () => this.scrollService.getPosition(this.scrollService.keyFor(this.activatedRoute)) ?? 0;
  private readonly scalePreference = new ScalePreference(this.localStorageService, {
    storageKey: SeriesBrowserComponent.SCALE_STORAGE_KEY,
    minScale: SeriesBrowserComponent.MIN_SCALE,
    maxScale: SeriesBrowserComponent.MAX_SCALE,
  });
  private readonly scaleFactor = this.scalePreference.scaleFactor;
  readonly screenWidth = signal(typeof window !== 'undefined' ? window.innerWidth : 1024);
  filterOptions: FilterOption[] = [];
  sortOptions: SortOption[] = [];

  @HostListener('window:resize')
  onResize(): void {
    this.screenWidth.set(window.innerWidth);
  }

  readonly isMobile = computed(() => this.screenWidth() <= 767);

  private readonly baseCardWidth = computed(() => this.isMobile()
    ? SeriesBrowserComponent.MOBILE_BASE_WIDTH
    : SeriesBrowserComponent.BASE_WIDTH
  );
  private readonly cardAspectRatio = computed(() => {
    const baseHeight = this.isMobile()
      ? SeriesBrowserComponent.MOBILE_BASE_HEIGHT
      : SeriesBrowserComponent.BASE_HEIGHT;
    return baseHeight / this.baseCardWidth();
  });
  private readonly minCardWidth = computed(() => Math.round(this.baseCardWidth() * this.scaleFactor()));
  readonly virtualGrid = createVirtualGrid({
    items: this.filteredSeries,
    scrollElement: this.scrollElement,
    minItemWidth: this.minCardWidth,
    gap: SeriesBrowserComponent.GRID_GAP,
    initialOffset: this.initialScrollOffset,
    fillItemWidth: true,
    estimateItemHeight: itemWidth => Math.round(itemWidth * this.cardAspectRatio()),
  });
  readonly currentCardScale = computed(() => this.virtualGrid.itemWidth() / this.baseCardWidth());

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

  adjustDesktopGridDensity(direction: 'smaller' | 'larger'): void {
    const currentColumns = this.virtualGrid.gridColumns();
    const columns = Math.max(1, direction === 'smaller'
      ? currentColumns + 1
      : currentColumns - 1);
    const viewportWidth = this.virtualGrid.viewportWidth() || this.screenWidth();
    this.virtualGrid.updatePreservingScrollPosition(() => {
      this.setScale(scaleForGridColumns(
        viewportWidth,
        SeriesBrowserComponent.GRID_GAP,
        columns,
        this.baseCardWidth(),
        SeriesBrowserComponent.MIN_SCALE,
        SeriesBrowserComponent.MAX_SCALE
      ));
    });
  }

  private setScale(scale: number): void {
    this.scalePreference.setScale(scale);
  }

  navigateToSeries(series: SeriesSummary): void {
    this.router.navigate(['/series', series.seriesName]);
  }

  private applyStatusFilter(series: SeriesSummary[], filterValue: string): SeriesSummary[] {
    switch (filterValue) {
      case 'not-started':
        return series.filter(s => s.seriesStatus === ReadStatus.UNREAD);
      case 'in-progress':
        return series.filter(s =>
          s.seriesStatus === ReadStatus.READING ||
          s.seriesStatus === ReadStatus.PARTIALLY_READ
        );
      case 'completed':
        return series.filter(s => s.seriesStatus === ReadStatus.READ);
      case 'abandoned':
        return series.filter(s =>
          s.seriesStatus === ReadStatus.ABANDONED ||
          s.seriesStatus === ReadStatus.WONT_READ
        );
      default:
        return series;
    }
  }

  private applySort(series: SeriesSummary[], sortBy: string): SeriesSummary[] {
    const sorted = [...series];
    switch (sortBy) {
      case 'name-asc':
        return sorted.sort((a, b) => a.seriesName.localeCompare(b.seriesName));
      case 'name-desc':
        return sorted.sort((a, b) => b.seriesName.localeCompare(a.seriesName));
      case 'book-count':
        return sorted.sort((a, b) => b.bookCount - a.bookCount);
      case 'progress':
        return sorted.sort((a, b) => b.progress - a.progress);
      case 'recently-read':
        return sorted.sort((a, b) => {
          const aTime = a.lastReadTime ? new Date(a.lastReadTime).getTime() : 0;
          const bTime = b.lastReadTime ? new Date(b.lastReadTime).getTime() : 0;
          return bTime - aTime;
        });
      case 'recently-added':
        return sorted.sort((a, b) => {
          const aTime = a.addedOn ? new Date(a.addedOn).getTime() : 0;
          const bTime = b.addedOn ? new Date(b.addedOn).getTime() : 0;
          return bTime - aTime;
        });
      default:
        return sorted;
    }
  }
}
