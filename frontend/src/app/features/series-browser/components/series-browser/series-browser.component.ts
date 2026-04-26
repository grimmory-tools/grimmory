import {Component, DestroyRef, HostListener, NgZone, computed, effect, inject, OnInit, signal, ViewChild} from '@angular/core';
import {computeGridColumns} from '../../../../shared/util/viewport.util';
import {NgStyle} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ProgressSpinner} from 'primeng/progressspinner';
import {InputText} from 'primeng/inputtext';
import {Select} from 'primeng/select';
import {Slider} from 'primeng/slider';
import {Popover} from 'primeng/popover';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {CdkFixedSizeVirtualScroll, CdkVirtualScrollViewport, CdkVirtualForOf} from '@angular/cdk/scrolling';
import {SeriesDataService} from '../../service/series-data.service';
import {SeriesSummary} from '../../model/series.model';
import {SeriesCardComponent} from '../series-card/series-card.component';
import {chunk} from '../../../../shared/util/array.util';
import {BookService} from '../../../book/service/book.service';
import {ReadStatus} from '../../../book/model/book.model';
import {PageTitleService} from '../../../../shared/service/page-title.service';
import {SeriesScalePreferenceService} from '../../service/series-scale-preference.service';
import {Router} from '@angular/router';

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
    NgStyle,
    FormsModule,
    ProgressSpinner,
    InputText,
    Select,
    Slider,
    Popover,
    TranslocoDirective,
    SeriesCardComponent,
    CdkVirtualScrollViewport,
    CdkFixedSizeVirtualScroll,
    CdkVirtualForOf,
  ]
})
export class SeriesBrowserComponent implements OnInit {

  private static readonly BASE_WIDTH = 230;
  private static readonly BASE_HEIGHT = 285;
  private static readonly MOBILE_BASE_WIDTH = 180;
  private static readonly MOBILE_BASE_HEIGHT = 250;
  private static readonly GRID_GAP = 20;
  private static readonly ROW_TOP_PADDING = 16;

  private seriesDataService = inject(SeriesDataService);
  private bookService = inject(BookService);
  private pageTitle = inject(PageTitleService);
  private t = inject(TranslocoService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  private zone = inject(NgZone);
  protected seriesScaleService = inject(SeriesScalePreferenceService);

  virtualScroller: CdkVirtualScrollViewport | undefined;
  private pendingViewportRefresh: number | undefined;

  @ViewChild(CdkVirtualScrollViewport)
  set scrollViewport(vp: CdkVirtualScrollViewport | undefined) {
    this.virtualScroller = vp;
    this.viewportResizeObserver?.disconnect();
    if (vp) {
      const el = vp.elementRef.nativeElement as HTMLElement;
      this.viewportWidth.set(this.measureViewportWidth(el));
      this.viewportResizeObserver = new ResizeObserver(entries => {
        this.zone.run(() => {
          this.viewportWidth.set(this.measureViewportWidth(el, entries[0]));
          this.scheduleViewportRefresh();
        });
      });
      this.viewportResizeObserver.observe(el);
      this.scheduleViewportRefresh();
    }
  }

  private readonly viewportWidth = signal(0);
  private viewportResizeObserver: ResizeObserver | undefined;

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

  screenWidth = window.innerWidth;
  filterOptions: FilterOption[] = [];
  sortOptions: SortOption[] = [];

  @HostListener('window:resize')
  onResize(): void {
    this.screenWidth = window.innerWidth;
    this.scheduleViewportRefresh();
  }

  get isMobile(): boolean {
    return this.screenWidth <= 767;
  }

  get cardWidth(): number {
    const base = this.isMobile
      ? SeriesBrowserComponent.MOBILE_BASE_WIDTH
      : SeriesBrowserComponent.BASE_WIDTH;
    return Math.round(base * this.seriesScaleService.scaleFactor());
  }

  get cardHeight(): number {
    const base = this.isMobile
      ? SeriesBrowserComponent.MOBILE_BASE_HEIGHT
      : SeriesBrowserComponent.BASE_HEIGHT;
    return Math.round(base * this.seriesScaleService.scaleFactor());
  }

  get rowHeight(): number {
    return this.cardHeight + SeriesBrowserComponent.ROW_TOP_PADDING;
  }

  readonly gridColumns = computed(() => {
    return computeGridColumns(this.viewportWidth(), this.cardWidth || 230, SeriesBrowserComponent.GRID_GAP);
  });

  readonly seriesRows = computed(() => {
    return chunk(this.filteredSeries(), this.gridColumns());
  });

  private readonly refreshViewportEffect = effect(() => {
    const hasRows = this.seriesRows().length > 0;
    const rowHeight = this.rowHeight;
    if (hasRows && rowHeight > 0) {
      this.scheduleViewportRefresh();
    }
  });

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
    this.destroyRef.onDestroy(() => {
      this.viewportResizeObserver?.disconnect();
      if (this.pendingViewportRefresh !== undefined) {
        window.cancelAnimationFrame(this.pendingViewportRefresh);
      }
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

  navigateToSeries(series: SeriesSummary): void {
    this.router.navigate(['/series', series.seriesName]);
  }

  private measureViewportWidth(el: HTMLElement, entry?: ResizeObserverEntry): number {
    const style = getComputedStyle(el);
    const horizontalPadding = this.parsePixelValue(style.paddingLeft) + this.parsePixelValue(style.paddingRight);
    const observedWidth = entry?.contentRect.width ?? el.clientWidth - horizontalPadding;
    return Math.max(0, Math.floor(observedWidth));
  }

  private parsePixelValue(value: string): number {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private scheduleViewportRefresh(): void {
    if (this.pendingViewportRefresh !== undefined) {
      return;
    }

    this.pendingViewportRefresh = window.requestAnimationFrame(() => {
      this.pendingViewportRefresh = undefined;
      this.refreshViewport();
    });
  }

  private refreshViewport(): void {
    if (!this.virtualScroller) {
      return;
    }

    const el = this.virtualScroller.elementRef.nativeElement as HTMLElement;
    this.viewportWidth.set(this.measureViewportWidth(el));
    this.virtualScroller.checkViewportSize();
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
