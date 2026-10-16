import {NgTemplateOutlet} from '@angular/common';
import {Component, Injector, afterNextRender, booleanAttribute, computed, ElementRef, inject, input, linkedSignal, model, output, signal, untracked} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideCheck, LucideChevronDown, LucideMinus, LucideSearch, LucideX} from '@lucide/angular';

import {cn} from '../../ui/cn';
import {IconDisplayComponent} from '../../components/icon-display/icon-display.component';
import {AppButtonComponent} from '../../ui/button/app-button.component';
import {AppInputComponent} from '../../ui/input/app-input.component';
import {AppRatingComponent} from '../../ui/rating/app-rating.component';
import {AppTagComponent} from '../../ui/tag/app-tag.component';
import {normalizeLocalSearchTerm} from '../../util/search-terms';
import {
  checkIndicatorBaseClass,
  checkIndicatorCheckedClass,
  checkIndicatorExcludedClass,
  checkIndicatorIconClass,
  checkIndicatorUncheckedClass,
} from '../../ui/checkbox/check-indicator.styles';
import {
  type BrowseFacetState,
  type BrowseFilterGroup,
  type BrowseFilterRangeCommit,
  type BrowseFilterToggle,
  type BrowseFilterValue,
} from '../facets';
import {BrowseFacetRangeInputsComponent} from './facet-range-inputs.component';

const COLLAPSED_VALUE_COUNT = 8;
const REVEAL_CLASS = 'grid transition-[grid-template-rows] duration-200 ease-out motion-reduce:transition-none';

@Component({
  selector: 'app-browse-filter-section',
  imports: [
    NgTemplateOutlet,
    TranslocoPipe,
    AppButtonComponent,
    AppInputComponent,
    AppRatingComponent,
    AppTagComponent,
    IconDisplayComponent,
    BrowseFacetRangeInputsComponent,
    LucideCheck,
    LucideChevronDown,
    LucideMinus,
    LucideSearch,
    LucideX,
  ],
  host: {class: 'block scroll-mt-[calc(var(--page-stuck-offset,0px)+8px)] border-t border-border/50 pt-2 first:border-t-0 first:pt-0'},
  templateUrl: './filter-section.component.html',
})
export class BrowseFilterSectionComponent<K extends string = string> {
  readonly group = input.required<BrowseFilterGroup<K>>();
  readonly open = model(false);
  readonly search = model('');
  readonly alwaysShowBoxes = input(false, {transform: booleanAttribute});
  readonly excludable = input(true);
  readonly toggleValue = output<BrowseFilterToggle<K>>();
  readonly commitRange = output<BrowseFilterRangeCommit<K>>();

  private readonly injector = inject(Injector);

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly everOpened = linkedSignal<boolean, boolean>({
    source: this.open,
    computation: (open, previous) => open || (previous?.value ?? false),
  });
  protected readonly expanded = signal(false);
  protected readonly searching = linkedSignal<string, boolean>({
    source: this.search,
    computation: (term, previous) => (previous?.value ?? false) || term !== '',
  });
  protected readonly revealed = computed(() => this.open() && !this.group().loading);
  protected readonly excludedLabelId = computed(() => `${this.group().key}-excluded`);

  protected readonly checkIconClass = checkIndicatorIconClass;
  protected readonly expandRowClass =
    'mt-0.5 flex min-h-7 w-full cursor-pointer items-center rounded-sm py-1 pl-7.5 ' +
    'text-left text-xs text-text-muted hover:text-text ' +
    'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary ' +
    'pointer-coarse:min-h-11 pointer-coarse:text-[13px] pointer-coarse:pl-9';

  protected toggleOpen(): void {
    this.open.update(open => !open);
  }

  protected readonly selectedCount = computed(() => {
    const group = this.group();
    return group.values.filter(item => this.state(item)).length + (rangeActive(group) ? 1 : 0);
  });

  protected revealClass(open: boolean): string {
    return cn(REVEAL_CLASS, open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]');
  }

  protected rangeRowClass(group: BrowseFilterGroup): string {
    return cn('px-2', group.values.length > 0 ? 'pt-3' : 'pt-1');
  }

  protected readonly foldLimit = computed(() => this.group().showAllValues ? Infinity : COLLAPSED_VALUE_COUNT);

  protected readonly foldClass = computed(() =>
    this.group().showAllValues || this.expanded()
      ? ''
      : 'min-h-[calc(8*1.75rem+1.875rem)] pointer-coarse:min-h-[calc(8*2.75rem+2.875rem)]');

  private readonly clicked = signal<{value: string; slot: number; from: BrowseFilterValue[]} | null>(null);
  private readonly values = computed(() => this.group().values);
  private readonly appliedClick = linkedSignal<BrowseFilterValue[], {value: string; slot: number} | null>({
    source: this.values,
    computation: (_values, previous) => {
      const click = untracked(this.clicked);
      return click && click.from === previous?.source ? click : null;
    },
  });

  protected readonly visibleValues = computed(() => {
    const query = this.activeQuery();
    const values = query ? this.matches(query) : this.group().values;
    const limit = query ? Infinity : this.foldLimit();
    const click = this.appliedClick();
    const item = click ? values.find(candidate => candidate.value === click.value) : undefined;
    if (!click || !item) {
      return this.fold(values, limit);
    }
    const shown = this.fold(values.filter(candidate => candidate !== item), limit - 1);
    shown.splice(Math.min(click.slot, shown.length), 0, item);
    return shown;
  });

  private fold(values: BrowseFilterValue[], limit: number): BrowseFilterValue[] {
    if (this.expanded()) {
      return [...values];
    }
    return foldValues(values, limit, item => this.state(item) !== null);
  }

  protected onExpandToggle(): void {
    this.expanded.update(expanded => !expanded);
    if (!this.expanded()) {
      afterNextRender(() => this.element.scrollIntoView({block: 'nearest'}), {injector: this.injector});
    }
  }

  protected readonly headerSearchable = computed(() => this.open() && this.group().values.length > this.foldLimit());

  protected toggleSearch(input: AppInputComponent): void {
    if (this.searching()) {
      this.searching.set(false);
      this.search.set('');
      return;
    }
    this.searching.set(true);
    afterNextRender(() => input.focus({preventScroll: true}), {injector: this.injector});
  }

  protected readonly activeQuery = computed(() => this.search().trim());

  private matches(query: string): BrowseFilterValue[] {
    const needle = normalizeLocalSearchTerm(query);
    return this.group().values.filter(item => normalizeLocalSearchTerm(item.label).includes(needle));
  }

  protected state(item: BrowseFilterValue): BrowseFacetState | null {
    const picks = this.group().picks;
    return picks ? picks.get(item.value) ?? null : item.state;
  }

  private nextState(item: BrowseFilterValue): BrowseFacetState | null {
    const current = this.state(item);
    if (current === null) {
      return 'included';
    }
    if (current === 'included' && this.excludable()) {
      return 'excluded';
    }
    return null;
  }

  protected onRowToggle(item: BrowseFilterValue): void {
    this.clicked.set({value: item.value, slot: this.visibleValues().indexOf(item), from: this.values()});
    this.toggleValue.emit({key: this.group().key, value: item.value, state: this.nextState(item)});
  }

  protected isZero(item: BrowseFilterValue): boolean {
    return item.count === 0 && item.state === null && !this.state(item);
  }

  protected rowClass(item: BrowseFilterValue): string {
    return cn(
      'group/frow flex min-h-7 w-full cursor-pointer items-center gap-2 rounded-md px-2 py-1 text-left text-text-secondary pointer-coarse:min-h-11 pointer-coarse:gap-2.5',
      'hover:bg-surface-hover hover:text-text',
      'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
      this.isZero(item) && 'opacity-45',
    );
  }

  protected boxClass(item: BrowseFilterValue): string {
    const state = this.state(item);
    return cn(
      checkIndicatorBaseClass,
      boxStateClass(state),
      !this.alwaysShowBoxes() && (state ? 'opacity-100' : 'opacity-0 group-hover/frow:opacity-100'),
    );
  }

  protected labelClass(item: BrowseFilterValue): string {
    const state = this.state(item);
    return cn(
      'min-w-0 flex-1 truncate',
      item.stars && 'flex items-center',
      state && 'font-[550] text-text',
      state === 'excluded' && 'line-through',
    );
  }
}

function boxStateClass(state: BrowseFacetState | null): string {
  if (state === 'excluded') {
    return checkIndicatorExcludedClass;
  }
  return state === 'included' ? checkIndicatorCheckedClass : checkIndicatorUncheckedClass;
}

function foldValues<T>(values: readonly T[], limit: number, kept: (item: T) => boolean): T[] {
  let room = limit - values.filter(kept).length;
  return values.filter(item => kept(item) || room-- > 0);
}

function rangeActive(group: BrowseFilterGroup): boolean {
  return group.range?.min != null || group.range?.max != null;
}
