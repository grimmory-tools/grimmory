import {ChangeDetectionStrategy, Component, EventEmitter, inject, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {BehaviorSubject, Observable, of, Subject, takeUntil} from 'rxjs';
import {Library} from '../../../model/library.model';
import {Shelf} from '../../../model/shelf.model';
import {EntityType} from '../book-browser.component';
import {Accordion, AccordionContent, AccordionHeader, AccordionPanel} from 'primeng/accordion';
import {CdkFixedSizeVirtualScroll, CdkVirtualForOf, CdkVirtualScrollViewport} from '@angular/cdk/scrolling';
import {AsyncPipe, NgClass} from '@angular/common';
import {Badge} from 'primeng/badge';
import {FormsModule} from '@angular/forms';
import {SelectButton} from 'primeng/selectbutton';
import {BookFilterMode, DEFAULT_VISIBLE_FILTERS, UserService, VisibleFilterType} from '../../../../settings/user-management/user.service';
import {MagicShelf} from '../../../../magic-shelf/service/magic-shelf.service';
import {Filter, FILTER_LABEL_KEYS, FilterType} from './book-filter.config';
import {BookFilterService} from './book-filter.service';
import {filter} from 'rxjs/operators';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

type FilterModeOption = { label: string; value: BookFilterMode };

@Component({
  selector: 'app-book-filter',
  templateUrl: './book-filter.component.html',
  styleUrls: ['./book-filter.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    Accordion, AccordionPanel, AccordionHeader, AccordionContent,
    CdkVirtualScrollViewport, CdkFixedSizeVirtualScroll, CdkVirtualForOf,
    NgClass, Badge, AsyncPipe, FormsModule, SelectButton,
    TranslocoDirective
  ]
})
export class BookFilterComponent implements OnInit, OnDestroy {
  @Input() entity$: Observable<Library | Shelf | MagicShelf | null> | undefined;
  @Input() entityType$: Observable<EntityType> | undefined;
  @Input() resetFilter$!: Subject<void>;
  @Input() showFilter = false;

  @Output() filterSelected = new EventEmitter<Record<string, unknown> | null>();
  @Output() filterModeChanged = new EventEmitter<BookFilterMode>();

  private readonly filterService = inject(BookFilterService);
  private readonly userService = inject(UserService);
  private readonly t = inject(TranslocoService);
  private readonly destroy$ = new Subject<void>();

  private readonly activeFilters$ = new BehaviorSubject<Record<string, unknown[]> | null>(null);
  private readonly filterMode$ = new BehaviorSubject<BookFilterMode>('and');

  activeFilters: Record<string, unknown[]> = {};
  filterStreams: Record<FilterType, Observable<Filter[]>> = {} as Record<FilterType, Observable<Filter[]>>;
  filterTypes: FilterType[] = [];
  visibleFilterTypes: FilterType[] = [];
  expandedPanels: number[] = [0];
  truncatedFilters: Record<string, boolean> = {};

  private _selectedFilterMode: BookFilterMode = 'and';
  private _visibleFilters: VisibleFilterType[] = [...DEFAULT_VISIBLE_FILTERS];

  readonly filterLabelKeys = FILTER_LABEL_KEYS;

  getFilterLabel(type: FilterType): string {
    const key = this.filterLabelKeys[type];
    return key ? this.t.translate(key) : type;
  }
  readonly filterModeOptions: FilterModeOption[] = [
    {label: 'AND', value: 'and'},
    {label: 'OR', value: 'or'},
    {label: '1', value: 'single'}
  ];

  get selectedFilterMode(): BookFilterMode {
    return this._selectedFilterMode;
  }

  set selectedFilterMode(mode: BookFilterMode) {
    if (mode === this._selectedFilterMode) return;
    this._selectedFilterMode = mode;
    this.filterMode$.next(mode);
    this.filterModeChanged.emit(mode);
    this.emitFilters();
  }

  ngOnInit(): void {
    this.subscribeToUserSettings();
    this.initializeFilterStreams();
    this.subscribeToReset();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  handleFilterClick(filterType: string, value: unknown): void {
    this._selectedFilterMode === 'single'
      ? this.handleSingleMode(filterType, value)
      : this.handleMultiMode(filterType, value);
    this.emitFilters();
  }

  setFilters(filters: Record<string, unknown>): void {
    this.activeFilters = {};
    for (const [key, value] of Object.entries(filters)) {
      const values = Array.isArray(value) ? value : [value];
      this.activeFilters[key] = values.map(v => this.filterService.processFilterValue(key, v));
    }
    this.emitFilters();
  }

  clearActiveFilter(): void {
    this.activeFilters = {};
    this.expandedPanels = [0];
    this.activeFilters$.next(null);
    this.filterSelected.emit(null);
  }

  onExpandedPanelsChange(value: string | number | string[] | number[] | null | undefined): void {
    if (Array.isArray(value)) {
      this.expandedPanels = value.map(Number);
    }
  }

  onFiltersChanged(): void {
    this.updateExpandedPanels();
  }

  getVirtualScrollHeight = (itemCount: number): number => Math.min(itemCount * 28, 440);

  trackByFilterType = (_: number, type: FilterType): string => type;

  trackByFilter = (_: number, f: Filter): unknown => this.getFilterValueId(f);

  getFilterValueId(f: Filter): unknown {
    const value = f.value;
    return typeof value === 'object' && value !== null && 'id' in value
      ? value.id
      : f.value;
  }

  getFilterValueDisplay(f: Filter): string {
    const value = f.value;
    if (typeof value === 'object' && value !== null && 'name' in value) {
      return String(value.name ?? '');
    }
    return String(value ?? '');
  }

  private subscribeToUserSettings(): void {
    this.userService.userState$.pipe(
      filter(state => !!state?.user && state.loaded),
      takeUntil(this.destroy$)
    ).subscribe(state => {
      const settings = state.user!.userSettings;
      this._visibleFilters = settings.visibleFilters ?? [...DEFAULT_VISIBLE_FILTERS];
      this.updateVisibleFilterTypes();
    });
  }

  private initializeFilterStreams(): void {
    const entity$ = this.entity$ ?? of(null);
    const entityType$ = this.entityType$ ?? of(EntityType.ALL_BOOKS);

    this.filterStreams = this.filterService.createFilterStreams(
      entity$,
      entityType$,
      this.activeFilters$,
      this.filterMode$
    );
    this.filterTypes = Object.keys(this.filterStreams) as FilterType[];
    this.updateVisibleFilterTypes();
    this.updateExpandedPanels();
  }

  private updateVisibleFilterTypes(): void {
    this.visibleFilterTypes = this._visibleFilters.filter(
      vf => this.filterTypes.includes(vf as FilterType)
    ) as FilterType[];
  }

  private subscribeToReset(): void {
    this.resetFilter$?.pipe(takeUntil(this.destroy$)).subscribe(() => this.clearActiveFilter());
  }

  private handleSingleMode(filterType: string, value: unknown): void {
    const id = this.extractId(value);
    const current = this.activeFilters[filterType];
    const isSame = current?.length === 1 && this.valuesMatch(current[0], id);

    this.activeFilters = isSame ? {} : {[filterType]: [id]};
  }

  private handleMultiMode(filterType: string, value: unknown): void {
    const id = this.extractId(value);

    if (!this.activeFilters[filterType]) {
      this.activeFilters[filterType] = [];
    }

    const arr = this.activeFilters[filterType];
    const index = arr.findIndex(v => this.valuesMatch(v, id));

    if (index > -1) {
      arr.splice(index, 1);
      if (arr.length === 0) delete this.activeFilters[filterType];
    } else {
      arr.push(id);
    }
  }

  private extractId(value: unknown): unknown {
    return typeof value === 'object' && value !== null && 'id' in value
      ? (value as { id: unknown }).id
      : value;
  }

  private valuesMatch(a: unknown, b: unknown): boolean {
    return a === b || String(a) === String(b);
  }

  private emitFilters(): void {
    const hasFilters = Object.keys(this.activeFilters).length > 0;
    const filtersToEmit = hasFilters ? {...this.activeFilters} : null;
    this.activeFilters$.next(filtersToEmit);
    this.filterSelected.emit(filtersToEmit);
  }

  private updateExpandedPanels(): void {
    const panels = new Set(this.expandedPanels);
    this.visibleFilterTypes.forEach((type, i) => {
      if (this.activeFilters[type]?.length) panels.add(i);
    });
    this.expandedPanels = panels.size > 0 ? [...panels] : [0];
  }
}
