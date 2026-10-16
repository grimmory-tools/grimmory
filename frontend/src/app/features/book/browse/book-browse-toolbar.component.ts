import {
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {
  LucideChevronDown,
  LucideChevronUp,
  LucideDynamicIcon,
  LucideEllipsis,
  LucideFunnel,
  LucideLayoutGrid,
  LucideListOrdered,
  LucideMinus,
  LucidePlus,
  LucideTableProperties,
  type LucideIconData,
} from '@lucide/angular';

import {type GridDensityDirection} from '../../../shared/util/grid-density.util';
import {DEFAULT_BOOK_SORT_TERMS, type BookSortTerm, type SortDirection} from '../data/book-query-params';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
import {connectedGroupClass, connectedItemClass} from '../../../shared/ui/connected-group';
import {AppRadioGroupComponent} from '../../../shared/ui/radio-group/app-radio-group.component';
import {AppMenuComponent} from '../../../shared/ui/menu/app-menu.component';
import {AppMenuCheckboxComponent} from '../../../shared/ui/menu/app-menu-checkbox.component';
import {AppMenuItemComponent} from '../../../shared/ui/menu/app-menu-item.component';
import {AppMenuRadioComponent} from '../../../shared/ui/menu/app-menu-radio.component';
import {AppMenuRadioGroupComponent} from '../../../shared/ui/menu/app-menu-radio-group.component';
import {AppMenuSectionComponent} from '../../../shared/ui/menu/app-menu-section.component';
import {AppMenuSeparatorComponent} from '../../../shared/ui/menu/app-menu-separator.component';
import {AppMenuTriggerDirective} from '../../../shared/ui/menu/app-menu-trigger.directive';
import {
  bookColumnSections,
  type BookBrowseColumnVisibilityChange,
  type BookColumnOption,
  type BookColumnSection,
} from './book-browse-columns';
import {bookSortDirectionIcon, bookSortField, type BookSortOption} from './book-browse-sort';
import {type BookBrowseViewMode} from './book-browse-url-state';
import {type LibraryShelfMenuTarget} from '../../../shared/layout/navigation/library-shelf-menu-target.model';
import {LibraryShelfMenuItemsComponent} from '../components/library-shelf-menu/library-shelf-menu-items.component';

@Component({
  selector: 'app-book-browse-toolbar',
  imports: [
    TranslocoPipe,
    AppButtonComponent,
    AppRadioGroupComponent,
    AppMenuComponent,
    AppMenuCheckboxComponent,
    AppMenuItemComponent,
    AppMenuRadioComponent,
    AppMenuRadioGroupComponent,
    AppMenuSectionComponent,
    AppMenuSeparatorComponent,
    AppMenuTriggerDirective,
    LibraryShelfMenuItemsComponent,
    LucideDynamicIcon,
    LucideEllipsis,
    LucideFunnel,
  ],
  host: {class: 'contents'},
  templateUrl: './book-browse-toolbar.component.html',
})
export class BookBrowseToolbarComponent {
  readonly sortOptions = input.required<readonly BookSortOption[]>();
  readonly sortTerms = input.required<readonly BookSortTerm[]>();
  readonly viewMode = input.required<BookBrowseViewMode>();
  readonly columnOptions = input.required<readonly BookColumnOption[]>();
  readonly densitySmallerDisabled = input.required<boolean>();
  readonly densityLargerDisabled = input.required<boolean>();
  readonly filtersOpen = input.required<boolean>();
  readonly actionTarget = input.required<LibraryShelfMenuTarget | null>();

  readonly sortChange = output<BookSortTerm>();
  readonly sortDirectionChange = output<BookSortTerm>();
  readonly randomSortRequested = output();
  readonly multiSortRequested = output();
  readonly viewModeChange = output<BookBrowseViewMode>();
  readonly columnVisibilityChange = output<BookBrowseColumnVisibilityChange>();
  readonly columnsReset = output();
  readonly densityChange = output<GridDensityDirection>();
  readonly filtersToggle = output();
  readonly mobileSelectToggle = output();

  protected readonly stepperItemClass =
    'w-10! flex-none justify-center px-0! text-text-muted pointer-coarse:w-12! [&_[data-menu-label]]:hidden';
  protected readonly smallerIcon: LucideIconData = LucideMinus.icon;
  protected readonly largerIcon: LucideIconData = LucidePlus.icon;
  protected readonly sortGroupClass = connectedGroupClass;
  protected readonly sortFieldButtonClass = computed(() => connectedItemClass({
    first: true,
    last: !this.activeSortHasSecondaryAction(),
  }));
  protected readonly sortDirectionButtonClass = connectedItemClass({first: false, last: true});

  protected readonly commonOptions = computed(() =>
    this.sortOptions().filter(option => option.group === 'common'));
  protected readonly moreOptions = computed(() =>
    this.sortOptions().filter(option => option.group === 'more'));
  protected readonly multiSortActive = computed(() => this.sortTerms().length > 1);
  protected readonly multiSortShortcut = computed(() =>
    this.multiSortActive() ? `+${this.sortTerms().length - 1}` : '');
  protected readonly moreSortsExpanded = signal(false);
  protected readonly moreSortsToggleIcon = computed<LucideIconData>(() =>
    this.moreSortsExpanded() ? LucideChevronUp.icon : LucideChevronDown.icon);

  protected onSortMenuOpened(): void {
    this.moreSortsExpanded.set(this.moreOptions().some(option => option.id === this.activeId()));
  }

  protected readonly gridIcon: LucideIconData = LucideLayoutGrid.icon;
  protected readonly tableIcon: LucideIconData = LucideTableProperties.icon;
  protected readonly multiSortIcon: LucideIconData = LucideListOrdered.icon;

  private readonly primaryTerm = computed<BookSortTerm>(() => this.sortTerms()[0] ?? DEFAULT_BOOK_SORT_TERMS[0]);
  protected readonly activeId = computed(() => this.primaryTerm().key);
  private readonly activeDirection = computed(() => this.primaryTerm().direction);
  private readonly activeOption = computed(() =>
    this.sortOptions().find(option => option.id === this.activeId()));
  protected readonly activeField = computed(() => this.activeOption() ?? bookSortField(this.activeId()));
  private readonly activeSortIsRandom = computed(() => this.activeField().kind === 'random');
  protected readonly activeSortCanToggle = computed(() =>
    this.sortOptions().length === 0 || (this.activeOption()?.directions.length ?? 0) > 1);
  protected readonly activeSortHasSecondaryAction = computed(() =>
    this.activeSortIsRandom() || this.activeSortCanToggle());
  protected readonly secondarySortActionLabelKey = computed(() => {
    if (this.activeSortIsRandom()) {
      return 'browse.toolbar.shuffleAgain';
    }
    return this.activeDirection() === 'asc'
      ? 'browse.toolbar.sortDescending'
      : 'browse.toolbar.sortAscending';
  });
  protected readonly activeSortIcon = computed(() =>
    bookSortDirectionIcon(this.activeId(), this.activeDirection()));
  protected readonly columnSections = computed<readonly BookColumnSection[]>(() => {
    return bookColumnSections(this.columnOptions());
  });

  protected selectView(viewMode: BookBrowseViewMode): void {
    if (viewMode !== this.viewMode()) {
      this.viewModeChange.emit(viewMode);
    }
  }

  protected onViewModeValue(viewMode: string | null): void {
    if (viewMode === 'grid' || viewMode === 'table') this.selectView(viewMode);
  }

  protected sortRankFor(option: BookSortOption): string {
    if (!this.multiSortActive()) {
      return '';
    }
    const index = this.sortTerms().findIndex(term => term.key === option.id);
    return index === -1 ? '' : `${index + 1}`;
  }

  protected directionIconFor(option: BookSortOption): LucideIconData | undefined {
    if (this.multiSortActive() || option.id !== this.activeId()) {
      return undefined;
    }
    return this.activeSortIcon();
  }

  protected onSelect(option: BookSortOption): void {
    const reselected = !this.multiSortActive() && option.id === this.activeId();
    const direction = reselected
      ? this.otherDirection(option) ?? option.defaultDirection
      : option.defaultDirection;
    this.sortChange.emit({key: option.id, direction});
  }

  protected activateSecondarySortAction(): void {
    if (this.activeSortIsRandom()) {
      this.randomSortRequested.emit();
      return;
    }
    const nextDirection = this.otherDirection(this.activeOption());
    if (nextDirection) {
      this.sortDirectionChange.emit({key: this.activeId(), direction: nextDirection});
    }
  }

  private otherDirection(option: BookSortOption | undefined): SortDirection | undefined {
    return option?.directions.find(direction => direction !== this.activeDirection());
  }
}
