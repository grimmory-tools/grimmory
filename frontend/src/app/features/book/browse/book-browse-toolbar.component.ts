import {
  Component,
  computed,
  input,
  output,
} from '@angular/core';
import {TranslocoPipe} from '@jsverse/transloco';
import {
  LucideEllipsis,
  LucideFunnel,
  LucideLayoutGrid,
  LucideTableProperties,
  type LucideIconData,
} from '@lucide/angular';

import {type GridDensityDirection} from '../../../shared/util/grid-density.util';
import {type BookSortTerm} from '../data/book-query-params';
import {AppButtonComponent} from '../../../shared/ui/button/app-button.component';
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
import {BOOK_FIELDS} from './book-browse-fields';
import {type BookSortOption} from './book-browse-sort';
import {BrowseSortControlComponent} from '../../../shared/browse/sort-control.component';
import {type BrowseViewMode} from '../../../shared/browse/url-state';
import {BrowseDensityMenuItemsComponent} from '../../../shared/browse/density-menu-items.component';
import {BrowseFilterMenuItemsComponent} from '../../../shared/browse/filter-menu-items.component';
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
    BrowseDensityMenuItemsComponent,
    BrowseFilterMenuItemsComponent,
    BrowseSortControlComponent,
    LucideEllipsis,
    LucideFunnel,
  ],
  host: {class: 'contents'},
  templateUrl: './book-browse-toolbar.component.html',
})
export class BookBrowseToolbarComponent {
  readonly sortOptions = input.required<readonly BookSortOption[]>();
  readonly sortTerms = input.required<readonly BookSortTerm[]>();
  readonly viewMode = input.required<BrowseViewMode>();
  readonly columnOptions = input.required<readonly BookColumnOption[]>();
  readonly densitySmallerDisabled = input.required<boolean>();
  readonly densityLargerDisabled = input.required<boolean>();
  readonly filtersOpen = input.required<boolean>();
  readonly matchAll = input.required<boolean>();
  readonly excludeOnTick = input.required<boolean>();
  readonly actionTarget = input.required<LibraryShelfMenuTarget | null>();
  readonly saveDefaultFor = input.required<string>();

  readonly sortTermsChange = output<readonly BookSortTerm[]>();
  readonly shuffleRequested = output();
  readonly sortDefaultSaved = output<readonly BookSortTerm[]>();
  readonly viewModeChange = output<BrowseViewMode>();
  readonly columnVisibilityChange = output<BookBrowseColumnVisibilityChange>();
  readonly columnsReset = output();
  readonly densityChange = output<GridDensityDirection>();
  readonly filtersToggle = output();
  readonly matchAllChange = output<boolean>();
  readonly excludeOnTickChange = output<boolean>();
  readonly mobileSelectToggle = output();

  protected readonly gridIcon: LucideIconData = LucideLayoutGrid.icon;
  protected readonly tableIcon: LucideIconData = LucideTableProperties.icon;
  protected readonly sortField = BOOK_FIELDS.sortField;

  protected readonly columnSections = computed<readonly BookColumnSection[]>(() => {
    return bookColumnSections(this.columnOptions());
  });

  protected selectView(viewMode: BrowseViewMode): void {
    if (viewMode !== this.viewMode()) {
      this.viewModeChange.emit(viewMode);
    }
  }

  protected onViewModeValue(viewMode: string | null): void {
    if (viewMode === 'grid' || viewMode === 'table') this.selectView(viewMode);
  }
}
