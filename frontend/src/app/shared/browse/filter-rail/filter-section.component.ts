import {Component, Injector, afterNextRender, booleanAttribute, computed, ElementRef, inject, input, linkedSignal, model, output, signal} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideCheck, LucideChevronDown, LucideMinus, LucideSearch, LucideSlidersHorizontal, LucideX} from '@lucide/angular';

import {cn} from '../../ui/cn';
import {IconDisplayComponent} from '../../components/icon-display/icon-display.component';
import {AppButtonComponent} from '../../ui/button/app-button.component';
import {AppInputComponent} from '../../ui/input/app-input.component';
import {AppMenuComponent} from '../../ui/menu/app-menu.component';
import {AppMenuRadioComponent} from '../../ui/menu/app-menu-radio.component';
import {AppMenuRadioGroupComponent} from '../../ui/menu/app-menu-radio-group.component';
import {AppMenuTriggerDirective} from '../../ui/menu/app-menu-trigger.directive';
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
  type BrowseFilterMatchAll,
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
    TranslocoPipe,
    AppButtonComponent,
    AppInputComponent,
    AppMenuComponent,
    AppMenuRadioComponent,
    AppMenuRadioGroupComponent,
    AppMenuTriggerDirective,
    AppRatingComponent,
    AppTagComponent,
    IconDisplayComponent,
    BrowseFacetRangeInputsComponent,
    LucideCheck,
    LucideChevronDown,
    LucideMinus,
    LucideSearch,
    LucideSlidersHorizontal,
    LucideX,
  ],
  host: {class: 'block scroll-mt-[calc(var(--page-stuck-offset,0px)+8px)] border-t border-border/50 pt-2 first:border-t-0 first:pt-0'},
  templateUrl: './filter-section.component.html',
})
export class BrowseFilterSectionComponent<K extends string = string> {
  private readonly injector = inject(Injector);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  readonly group = input.required<BrowseFilterGroup<K>>();
  readonly isOpen = model(false);
  readonly search = model('');
  readonly alwaysShowBoxes = input(false, {transform: booleanAttribute});
  readonly toggleValue = output<BrowseFilterToggle<K>>();
  readonly matchAllChange = output<BrowseFilterMatchAll<K>>();
  readonly commitRange = output<BrowseFilterRangeCommit<K>>();
  protected readonly hasEverOpened = linkedSignal<boolean, boolean>({
    source: this.isOpen,
    computation: (isOpen, previous) => isOpen || (previous?.value ?? false),
  });
  protected readonly expanded = signal(false);
  protected readonly searching = linkedSignal<string, boolean>({
    source: this.search,
    computation: (term, previous) => (previous?.value ?? false) || term !== '',
  });
  protected readonly revealed = computed(() => this.isOpen() && !this.group().loading);
  protected readonly excludedLabelId = computed(() => `${this.group().key}-excluded`);

  protected readonly checkIconClass = checkIndicatorIconClass;
  protected readonly includeClass = computed(() =>
    cn(
      'flex min-w-0 flex-1 cursor-pointer items-center gap-2 rounded-md py-1 pl-2 text-left pointer-coarse:gap-2.5',
      'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
      !this.alwaysShowBoxes() && 'pointer-fine:pr-2',
    ));
  protected readonly expandRowClass =
    'mt-0.5 flex min-h-7 w-full cursor-pointer items-center rounded-sm py-1 pl-7.5 ' +
    'text-left text-xs text-text-muted hover:text-text ' +
    'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary ' +
    'pointer-coarse:min-h-11 pointer-coarse:text-[13px] pointer-coarse:pl-9';

  protected readonly matchable = computed(() => this.isOpen() && this.group().multiValued);
  protected readonly headerButtonClass = computed(() =>
    cn(
      'flex min-h-9 flex-1 cursor-pointer items-center gap-1.5 text-left pointer-coarse:min-h-12',
      'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
      this.matchable() && this.headerSearchable() ? 'pr-16 pointer-coarse:pr-22' : 'pr-8 pointer-coarse:pr-12',
    ));

  protected onMatchAll(value: string): void {
    this.matchAllChange.emit({key: this.group().key, matchAll: value === 'all'});
  }

  protected toggleOpen(): void {
    this.isOpen.update(isOpen => !isOpen);
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

  protected readonly foldClass = computed(() => {
    if (this.activeQuery()) {
      return 'min-h-56 pointer-coarse:min-h-[22rem]';
    }
    return this.group().showAllValues || this.expanded()
      ? ''
      : 'min-h-[calc(8*1.75rem+1.875rem)] pointer-coarse:min-h-[calc(8*2.75rem+2.875rem)]';
  });

  private readonly held = linkedSignal<readonly [boolean, boolean, string], {value: string; slot: number} | null>({
    source: () => [this.isOpen(), this.expanded(), this.activeQuery()],
    computation: () => null,
  });

  private readonly visibleValues = computed(() => {
    const query = this.activeQuery();
    const values = query ? this.matches(query) : this.group().values;
    const limit = query || this.expanded() ? Infinity : this.foldLimit();
    const held = this.held();
    const kept = (candidate: BrowseFilterValue) => this.state(candidate) !== null;
    const item = held ? values.find(candidate => candidate.value === held.value) : undefined;
    if (!held || !item) {
      return foldValues(values, limit, kept);
    }
    const shown = foldValues(values.filter(candidate => candidate !== item), limit - 1, kept);
    shown.splice(Math.min(held.slot, shown.length), 0, item);
    return shown;
  });

  protected onExpandToggle(): void {
    this.expanded.update(expanded => !expanded);
    if (!this.expanded()) {
      afterNextRender(() => this.element.scrollIntoView({block: 'nearest'}), {injector: this.injector});
    }
  }

  protected readonly headerSearchable = computed(() => this.isOpen() && this.group().values.length > this.foldLimit());

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

  private state(item: BrowseFilterValue): BrowseFacetState | null {
    return this.group().picks?.get(item.value) ?? null;
  }

  protected readonly rows = computed(() => {
    const boxes = this.alwaysShowBoxes();
    return this.visibleValues().map(item => {
      const state = this.state(item);
      const zero = item.count === 0 && state === null && this.held()?.value !== item.value;
      const trailing = boxes ? 'boxes' : state === 'excluded' ? 'excluded' : zero ? 'count' : 'swap';
      return {
        item,
        state,
        zero,
        rowClass: cn(
          'group/frow relative flex min-h-7 w-full items-stretch rounded-md text-text-secondary pointer-coarse:min-h-11',
          'hover:bg-surface-hover hover:text-text',
          zero && 'opacity-45',
        ),
        boxClass: cn(
          checkIndicatorBaseClass,
          state === 'included' ? checkIndicatorCheckedClass : checkIndicatorUncheckedClass,
          !boxes && state !== 'included' && 'opacity-0 group-hover/frow:opacity-100',
        ),
        labelClass: cn(
          'min-w-0 flex-1 truncate',
          item.stars && 'flex items-center',
          state && 'font-[550] text-text',
          state === 'excluded' && 'line-through',
        ),
        countClass: cn(
          'min-w-6 shrink-0 text-right text-[11.5px] tabular-nums text-text-muted pointer-coarse:text-xs',
          trailing === 'excluded' && 'pointer-fine:invisible',
          trailing === 'swap' && 'pointer-fine:group-hover/frow:invisible pointer-fine:group-has-[:focus-visible]/frow:invisible',
        ),
        excludeClass: cn(
          'group/fexc flex shrink-0 cursor-pointer items-center rounded-md px-2 pointer-coarse:px-3',
          'focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
          !boxes && 'pointer-fine:absolute pointer-fine:inset-y-0 pointer-fine:right-0',
          (trailing === 'swap' || trailing === 'count') && 'opacity-0 pointer-coarse:opacity-100',
          trailing === 'swap' && 'group-hover/frow:opacity-100 group-has-[:focus-visible]/frow:opacity-100',
        ),
        excludeBoxClass: state === 'excluded'
          ? cn(checkIndicatorBaseClass, checkIndicatorExcludedClass)
          : cn(checkIndicatorBaseClass, checkIndicatorUncheckedClass, 'text-text-muted/60 group-hover/fexc:border-danger group-hover/fexc:text-danger'),
      };
    });
  });

  protected onRowToggle(slot: number, target: BrowseFacetState): void {
    const {item, state} = this.rows()[slot];
    this.held.set({value: item.value, slot});
    this.toggleValue.emit({key: this.group().key, value: item.value, state: state === target ? null : target});
  }
}

function foldValues<T>(values: readonly T[], limit: number, kept: (item: T) => boolean): T[] {
  let room = limit - values.filter(kept).length;
  return values.filter(item => kept(item) || room-- > 0);
}

function rangeActive(group: BrowseFilterGroup): boolean {
  return group.range?.min != null || group.range?.max != null;
}
