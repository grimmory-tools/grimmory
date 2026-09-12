import {NgTemplateOutlet} from '@angular/common';
import {Component, Injector, afterNextRender, booleanAttribute, computed, ElementRef, inject, input, output, signal, type OnInit} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {LucideCheck, LucideChevronDown, LucideSearch, LucideX} from '@lucide/angular';

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
  checkIndicatorIconClass,
  checkIndicatorUncheckedClass,
} from '../../ui/checkbox/check-indicator.styles';
import {type BrowseFilterGroup, type BrowseFilterRangeCommit, type BrowseFilterToggle, type BrowseFilterValue} from '../facets';
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
    LucideSearch,
    LucideX,
  ],
  host: {class: 'block scroll-mt-[calc(var(--page-stuck-offset,0px)+8px)] border-t border-border/50 pt-2 first:border-t-0 first:pt-0'},
  templateUrl: './filter-section.component.html',
})
export class BrowseFilterSectionComponent<K extends string = string> implements OnInit {
  readonly group = input.required<BrowseFilterGroup<K>>();
  readonly alwaysShowBoxes = input(false, {transform: booleanAttribute});
  readonly toggleValue = output<BrowseFilterToggle<K>>();
  readonly commitRange = output<BrowseFilterRangeCommit<K>>();

  private readonly injector = inject(Injector);

  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly disclosure = signal<'unopened' | 'open' | 'closed'>('unopened');
  protected readonly expanded = signal(false);
  protected readonly searching = signal(false);
  protected readonly search = signal('');
  protected readonly isOpen = computed(() => this.disclosure() === 'open');

  protected readonly checkIconClass = checkIndicatorIconClass;
  protected readonly expandRowClass =
    'mt-0.5 flex min-h-7 w-full cursor-pointer items-center rounded-sm py-1 pl-7.5 ' +
    'text-left text-xs text-text-muted hover:text-text ' +
    'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary ' +
    'pointer-coarse:min-h-11 pointer-coarse:text-[13px] pointer-coarse:pl-9';

  ngOnInit(): void {
    const group = this.group();
    if (group.defaultOpen || this.selectedCount() > 0) {
      this.disclosure.set('open');
    }
  }

  protected toggleOpen(): void {
    this.disclosure.set(this.isOpen() ? 'closed' : 'open');
  }

  protected readonly selectedCount = computed(() => {
    const group = this.group();
    return group.values.filter(item => item.selected).length + (rangeActive(group) ? 1 : 0);
  });

  protected revealClass(open: boolean): string {
    return cn(REVEAL_CLASS, open ? 'grid-rows-[1fr]' : 'grid-rows-[0fr]');
  }

  protected rangeRowClass(group: BrowseFilterGroup): string {
    return cn('px-2', group.values.length > 0 ? 'pt-3' : 'pt-1');
  }

  protected readonly foldLimit = computed(() => this.group().showAllValues ? Infinity : COLLAPSED_VALUE_COUNT);

  protected readonly visibleValues = computed(() => {
    const group = this.group();
    if (this.expanded()) {
      return group.values;
    }
    const limit = this.foldLimit();
    return group.values.filter((item, index) => index < limit || item.selected);
  });

  protected onExpandToggle(): void {
    this.expanded.update(expanded => !expanded);
    if (!this.expanded()) {
      afterNextRender(() => this.element.scrollIntoView({block: 'nearest'}), {injector: this.injector});
    }
  }

  protected readonly headerSearchable = computed(() => this.isOpen() && this.group().values.length > this.foldLimit());

  protected toggleSearch(input: AppInputComponent): void {
    this.searching.update(searching => !searching);
    if (this.searching()) {
      afterNextRender(() => input.focus({preventScroll: true}), {injector: this.injector});
    }
  }

  protected readonly activeQuery = computed(() => this.searching() ? this.search().trim() : '');

  protected matches(query: string): BrowseFilterValue[] {
    const needle = normalizeLocalSearchTerm(query);
    return this.group().values.filter(item => normalizeLocalSearchTerm(item.label).includes(needle));
  }

  protected onRowToggle(item: BrowseFilterValue): void {
    this.toggleValue.emit({key: this.group().key, value: item.value, selected: !item.selected});
  }

  protected isZero(item: BrowseFilterValue): boolean {
    return item.count === 0 && !item.selected;
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
    return cn(
      checkIndicatorBaseClass,
      item.selected ? checkIndicatorCheckedClass : checkIndicatorUncheckedClass,
      !this.alwaysShowBoxes() && (item.selected ? 'opacity-100' : 'opacity-0 group-hover/frow:opacity-100'),
    );
  }

  protected labelClass(item: BrowseFilterValue): string {
    return cn(
      'min-w-0 flex-1 truncate',
      item.stars && 'flex items-center',
      item.selected && 'font-[550] text-text',
    );
  }
}

function rangeActive(group: BrowseFilterGroup): boolean {
  return group.range?.min != null || group.range?.max != null;
}
