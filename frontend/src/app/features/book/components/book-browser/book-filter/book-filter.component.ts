import {ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, Signal} from '@angular/core';
import {Accordion, AccordionContent, AccordionHeader, AccordionPanel} from 'primeng/accordion';
import {CdkFixedSizeVirtualScroll, CdkVirtualForOf, CdkVirtualScrollViewport} from '@angular/cdk/scrolling';
import {NgClass} from '@angular/common';
import {Badge} from 'primeng/badge';
import {FormsModule} from '@angular/forms';
import {SelectButton} from 'primeng/selectbutton';
import {BookFilterMode, DEFAULT_VISIBLE_FILTERS, UserService, VisibleFilterType} from '../../../../settings/user-management/user.service';
import {Filter, FILTER_LABEL_KEYS, FilterType} from './book-filter.config';
import {BookFilterService} from './book-filter.service';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';

interface FilterModeOption {
  label: string;
  value: BookFilterMode;
}

@Component({
  selector: 'app-book-filter',
  templateUrl: './book-filter.component.html',
  styleUrls: ['./book-filter.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
  imports: [
    Accordion, AccordionPanel, AccordionHeader, AccordionContent,
    CdkVirtualScrollViewport, CdkFixedSizeVirtualScroll, CdkVirtualForOf,
    NgClass, Badge, FormsModule, SelectButton,
    TranslocoDirective
  ]
})
export class BookFilterComponent {
  readonly showFilter = input(false);

  readonly filterSelected = output<Record<string, unknown> | null>();
  readonly filterModeChanged = output<BookFilterMode>();

  private readonly filterService = inject(BookFilterService);
  private readonly userService = inject(UserService);
  private readonly t = inject(TranslocoService);

  /** Stable reference to the singleton filter signals — never recreated. */
  readonly filterSignals: Record<FilterType, Signal<Filter[]>> = this.filterService.filterSignals;
  readonly filterTypes: FilterType[] = Object.keys(this.filterSignals) as FilterType[];

  readonly activeFilters = signal<Record<string, unknown[]>>({});
  readonly expandedPanels = signal<number[]>([]);

  private readonly visibleFilters = signal<VisibleFilterType[]>([...DEFAULT_VISIBLE_FILTERS]);

  readonly visibleFilterTypes = computed(() => {
    const vf = this.visibleFilters();
    return vf.filter(f => this.filterTypes.includes(f as FilterType)) as FilterType[];
  });

  readonly filterLabelKeys = FILTER_LABEL_KEYS;

  readonly filterModeOptions: FilterModeOption[] = [
    {label: 'AND', value: 'and'},
    {label: 'OR', value: 'or'},
    {label: 'NOT', value: 'not'},
    {label: '1', value: 'single'}
  ];

  private readonly _selectedFilterMode = signal<BookFilterMode>('and');
  readonly selectedFilterMode = this._selectedFilterMode.asReadonly();

  private readonly syncUserSettings = effect(() => {
    const user = this.userService.currentUser();
    if (!user) return;
    this.visibleFilters.set(user.userSettings.visibleFilters ?? [...DEFAULT_VISIBLE_FILTERS]);
  });

  onFilterModeChange(mode: BookFilterMode): void {
    if (mode === this._selectedFilterMode()) return;
    this._selectedFilterMode.set(mode);
    this.filterModeChanged.emit(mode);
    this.emitFilters();
  }

  getFilterLabel(type: FilterType): string {
    const key = this.filterLabelKeys[type];
    return key ? this.t.translate(key) : type;
  }

  handleFilterClick(filterType: string, value: unknown): void {
    if (this._selectedFilterMode() === 'single') {
      this.handleSingleMode(filterType, value);
    } else {
      this.handleMultiMode(filterType, value);
    }
    this.emitFilters();
  }

  setFilters(filters: Record<string, unknown>): void {
    const result: Record<string, unknown[]> = {};
    for (const [key, value] of Object.entries(filters)) {
      const values = Array.isArray(value) ? value : [value];
      result[key] = values.map(v => this.filterService.processFilterValue(key, v));
    }
    this.activeFilters.set(result);
    this.emitFilters();
  }

  clearActiveFilter(): void {
    this.activeFilters.set({});
    this.expandedPanels.set([]);
    this.filterSelected.emit(null);
  }

  onExpandedPanelsChange(value: string | number | string[] | number[] | null | undefined): void {
    if (Array.isArray(value)) {
      const panels = value.map(Number);
      if (JSON.stringify(panels) !== JSON.stringify(this.expandedPanels())) {
        this.expandedPanels.set(panels);
      }
    }
  }

  getVirtualScrollHeight(itemCount: number): number {
    return Math.min(itemCount * 28, 440);
  }

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

  isActive(filterType: string, filterId: unknown): boolean {
    const active = this.activeFilters()[filterType];
    if (!active) return false;
    return active.some(v => v === filterId || String(v) === String(filterId));
  }

  private handleSingleMode(filterType: string, value: unknown): void {
    const id = this.extractId(value);
    const current = this.activeFilters()[filterType];
    const isSame = current?.length === 1 && this.valuesMatch(current[0], id);
    this.activeFilters.set(isSame ? {} : {[filterType]: [id]});
  }

  private handleMultiMode(filterType: string, value: unknown): void {
    const id = this.extractId(value);
    const current = {...this.activeFilters()};
    const arr = [...(current[filterType] ?? [])];

    const index = arr.findIndex(v => this.valuesMatch(v, id));
    if (index > -1) {
      arr.splice(index, 1);
      if (arr.length === 0) {
        delete current[filterType];
      } else {
        current[filterType] = arr;
      }
    } else {
      current[filterType] = [...arr, id];
    }
    this.activeFilters.set(current);
  }

  private extractId(value: unknown): unknown {
    return typeof value === 'object' && value !== null && 'id' in value
      ? (value as {id: unknown}).id
      : value;
  }

  private valuesMatch(a: unknown, b: unknown): boolean {
    return a === b || String(a) === String(b);
  }

  private emitFilters(): void {
    const filters = this.activeFilters();
    const hasFilters = Object.keys(filters).length > 0;
    this.filterSelected.emit(hasFilters ? {...filters} : null);

    // Update expanded panels to show panels with active filters
    const panels = new Set<number>();
    const types = this.visibleFilterTypes();
    types.forEach((type, i) => {
      if (filters[type]?.length) panels.add(i);
    });
    this.expandedPanels.set([...panels]);
  }
}
