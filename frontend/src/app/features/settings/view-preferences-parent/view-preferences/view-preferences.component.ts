import {Component, DestroyRef, effect, ElementRef, inject, Injector, OnInit, viewChild} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Button} from 'primeng/button';

import {MessageService} from 'primeng/api';
import {Select} from 'primeng/select';
import {TableModule} from 'primeng/table';
import {DEFAULT_VISIBLE_SORT_FIELDS, SortCriterion, User, UserService} from '../../user-management/user.service';
import {LibraryService} from '../../../book/service/library.service';
import {ShelfService} from '../../../book/service/shelf.service';
import {MagicShelfService} from '../../../magic-shelf/service/magic-shelf.service';
import {FormsModule} from '@angular/forms';

import {Tooltip} from 'primeng/tooltip';
import {ToggleSwitch} from 'primeng/toggleswitch';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {SortDirection, SortOption} from '../../../book/model/sort.model';
import {MultiSortPopoverComponent} from '../../../book/components/book-browser/sorting/multi-sort-popover/multi-sort-popover.component';
import {Popover} from 'primeng/popover';
import {CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray} from '@angular/cdk/drag-drop';

@Component({
  selector: 'app-view-preferences',
  standalone: true,
  imports: [
    Select,
    FormsModule,
    Button,
    TableModule,
    Tooltip,
    ToggleSwitch,
    TranslocoDirective,
    MultiSortPopoverComponent,
    Popover,
    CdkDropList,
    CdkDrag,
    CdkDragHandle
  ],
  templateUrl: './view-preferences.component.html',
  styleUrl: './view-preferences.component.scss'
})
export class ViewPreferencesComponent implements OnInit {
  private t = inject(TranslocoService);

  private readonly sortOptionDefs: {field: string; translationKey: string}[] = [
    {field: 'title', translationKey: 'sortTitle'},
    {field: 'fileName', translationKey: 'sortFileName'},
    {field: 'filePath', translationKey: 'sortFilePath'},
    {field: 'author', translationKey: 'sortAuthor'},
    {field: 'authorSurnameVorname', translationKey: 'sortAuthorSurname'},
    {field: 'seriesName', translationKey: 'sortSeriesName'},
    {field: 'seriesNumber', translationKey: 'sortSeriesNumber'},
    {field: 'lastReadTime', translationKey: 'sortLastRead'},
    {field: 'personalRating', translationKey: 'sortPersonalRating'},
    {field: 'addedOn', translationKey: 'sortAddedOn'},
    {field: 'fileSizeKb', translationKey: 'sortFileSize'},
    {field: 'locked', translationKey: 'sortLocked'},
    {field: 'publisher', translationKey: 'sortPublisher'},
    {field: 'publishedDate', translationKey: 'sortPublishedDate'},
    {field: 'readStatus', translationKey: 'sortReadStatus'},
    {field: 'dateFinished', translationKey: 'sortDateFinished'},
    {field: 'readingProgress', translationKey: 'sortReadingProgress'},
    {field: 'bookType', translationKey: 'sortBookType'},
    {field: 'amazonRating', translationKey: 'sortAmazonRating'},
    {field: 'amazonReviewCount', translationKey: 'sortAmazonCount'},
    {field: 'goodreadsRating', translationKey: 'sortGoodreadsRating'},
    {field: 'goodreadsReviewCount', translationKey: 'sortGoodreadsCount'},
    {field: 'hardcoverRating', translationKey: 'sortHardcoverRating'},
    {field: 'hardcoverReviewCount', translationKey: 'sortHardcoverCount'},
    {field: 'ranobedbRating', translationKey: 'sortRanobedbRating'},
    {field: 'narrator', translationKey: 'sortNarrator'},
    {field: 'pageCount', translationKey: 'sortPages'},
    {field: 'random', translationKey: 'sortRandom'},
  ];

  sortOptions: {label: string; field: string; translationKey: string}[] = [];

  entityTypeOptions: {label: string; value: string; translationKey: string}[] = [];

  viewModeOptions: {label: string; value: string; translationKey: string}[] = [];

  get libraryOptions(): { label: string; value: number }[] {
    return this.libraryService.libraries()
      .filter(library => library.id !== undefined)
      .map(library => ({label: library.name, value: library.id!}));
  }
  get shelfOptions(): { label: string; value: number }[] {
    return this.shelfService.shelves()
      .filter(shelf => shelf.id !== undefined)
      .map(shelf => ({label: shelf.name, value: shelf.id!}));
  }
  get magicShelfOptions(): { label: string; value: number }[] {
    return this.magicShelfService.shelves()
      .filter(shelf => shelf.id !== undefined)
      .map(shelf => ({label: shelf.name, value: shelf.id!}));
  }

  selectedSort: string = 'title';
  selectedSortDir: 'ASC' | 'DESC' = 'ASC';
  selectedView: 'GRID' | 'TABLE' = 'GRID';
  autoSaveMetadata: boolean = false;
  sortCriteria: SortCriterion[] = [];

  // SortOption[] versions for the multi-sort-popover component
  globalSortAsOptions: SortOption[] = [];
  allSortAsOptions: SortOption[] = [];

  // Visible sort fields configuration
  visibleSortFields: string[] = [];
  selectedAddSortField: string | null = null;
  readonly minSortFields = 3;
  readonly maxSortFields = 27;

  private readonly sortFieldList = viewChild<ElementRef<HTMLElement>>('sortFieldList');

  overrides: {
    entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF';
    library: number;
    sort: string;
    sortDir: 'ASC' | 'DESC';
    sortCriteria: SortCriterion[];
    sortCriteriaAsOptions: SortOption[];
    view: 'GRID' | 'TABLE';
  }[] = [];

  private user: User | null = null;
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  private libraryService = inject(LibraryService);
  private shelfService = inject(ShelfService);
  private magicShelfService = inject(MagicShelfService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);
  private readonly currentUser = this.userService.currentUser;
  private hasInitializedPreferences = false;

  ngOnInit(): void {
    this.rebuildTranslatedLabels();
    this.t.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.rebuildTranslatedLabels();
      this.allSortAsOptions = this.sortOptions.map(o => ({
        label: this.t.translate('settingsView.librarySort.' + o.translationKey),
        field: o.field,
        direction: SortDirection.ASCENDING
      }));
      this.globalSortAsOptions = this.toSortOptions(this.sortCriteria);
      this.overrides.forEach(o => {
        o.sortCriteriaAsOptions = this.toSortOptions(o.sortCriteria);
      });
    });

    effect(() => {
      const user = this.currentUser();
      if (this.hasInitializedPreferences || !user) {
        return;
      }

      this.hasInitializedPreferences = true;
      this.user = user;
      const prefs = user.userSettings?.entityViewPreferences;
      const global = prefs?.global;
      this.selectedSort = global?.sortKey ?? 'title';
      this.selectedSortDir = global?.sortDir ?? 'ASC';
      this.selectedView = global?.view ?? 'GRID';
      this.autoSaveMetadata = user.userSettings?.autoSaveMetadata ?? false;

      if (global?.sortCriteria && global.sortCriteria.length > 0) {
        this.sortCriteria = [...global.sortCriteria];
      } else {
        this.sortCriteria = [{field: this.selectedSort, direction: this.selectedSortDir}];
      }

      this.allSortAsOptions = this.sortOptions.map(option => ({
        label: this.t.translate('settingsView.librarySort.' + option.translationKey),
        field: option.field,
        direction: SortDirection.ASCENDING
      }));
      this.globalSortAsOptions = this.toSortOptions(this.sortCriteria);

      this.visibleSortFields = user.userSettings?.visibleSortFields
        ? [...user.userSettings.visibleSortFields]
        : [...DEFAULT_VISIBLE_SORT_FIELDS];

      this.overrides = (prefs?.overrides ?? []).map(override => {
        const sortCriteria = override.preferences.sortCriteria?.length
          ? [...override.preferences.sortCriteria]
          : [{field: override.preferences.sortKey, direction: override.preferences.sortDir ?? 'ASC'} as SortCriterion];
        return {
          entityType: override.entityType,
          library: override.entityId,
          sort: override.preferences.sortKey,
          sortDir: override.preferences.sortDir ?? 'ASC',
          sortCriteria,
          sortCriteriaAsOptions: this.toSortOptions(sortCriteria),
          view: override.preferences.view ?? 'GRID'
        };
      });
    }, {injector: this.injector});
  }

  private rebuildTranslatedLabels(): void {
    this.sortOptions = this.sortOptionDefs.map(o => ({
      label: this.t.translate('settingsView.librarySort.' + o.translationKey),
      field: o.field,
      translationKey: o.translationKey
    }));
    this.entityTypeOptions = [
      {label: this.t.translate('settingsView.librarySort.entityLibrary'), value: 'LIBRARY', translationKey: 'entityLibrary'},
      {label: this.t.translate('settingsView.librarySort.entityShelf'), value: 'SHELF', translationKey: 'entityShelf'},
      {label: this.t.translate('settingsView.librarySort.entityMagicShelf'), value: 'MAGIC_SHELF', translationKey: 'entityMagicShelf'}
    ];
    this.viewModeOptions = [
      {label: this.t.translate('settingsView.librarySort.viewGrid'), value: 'GRID', translationKey: 'viewGrid'},
      {label: this.t.translate('settingsView.librarySort.viewTable'), value: 'TABLE', translationKey: 'viewTable'}
    ];
  }

  getAvailableEntities(index: number, type: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF') {
    const selected = this.overrides.map((o, i) => i !== index ? o.library : null);
    let source: { label: string; value: number }[];
    switch (type) {
      case 'LIBRARY':
        source = this.libraryOptions;
        break;
      case 'SHELF':
        source = this.shelfOptions;
        break;
      case 'MAGIC_SHELF':
        source = this.magicShelfOptions;
        break;
      default:
        source = [];
    }
    return source.filter(opt => !selected.includes(opt.value) || this.overrides[index]?.library === opt.value);
  }

  get availableLibraries() {
    const used = new Set(this.overrides.map(o => `${o.entityType}_${o.library}`));

    const withEntityType = (options: { label: string; value: number }[], entityType: 'LIBRARY' | 'SHELF' | 'MAGIC_SHELF') =>
      options.map(opt => ({...opt, entityType}));

    return [...withEntityType(this.libraryOptions, 'LIBRARY'),
      ...withEntityType(this.shelfOptions, 'SHELF'),
      ...withEntityType(this.magicShelfOptions, 'MAGIC_SHELF')]
      .filter(opt => !used.has(`${opt.entityType}_${opt.value}`));
  }

  addOverride(): void {
    const next = this.availableLibraries[0];
    if (next) {
      const defaultCriteria: SortCriterion[] = [{field: 'title', direction: 'ASC'}];
      this.overrides.push({
        entityType: next.entityType,
        library: next.value,
        sort: 'title',
        sortDir: 'ASC',
        sortCriteria: defaultCriteria,
        sortCriteriaAsOptions: this.toSortOptions(defaultCriteria),
        view: 'GRID'
      });
    }
  }

  removeOverride(index: number): void {
    this.overrides.splice(index, 1);
  }

  // Conversion helpers
  private toSortOptions(criteria: SortCriterion[]): SortOption[] {
    return criteria.map(c => ({
      label: this.t.translate('settingsView.librarySort.' + (this.sortOptions.find(o => o.field === c.field)?.translationKey ?? c.field)),
      field: c.field,
      direction: c.direction === 'ASC' ? SortDirection.ASCENDING : SortDirection.DESCENDING
    }));
  }

  private toSortCriteria(options: SortOption[]): SortCriterion[] {
    return options.map(o => ({
      field: o.field,
      direction: o.direction === SortDirection.ASCENDING ? 'ASC' as const : 'DESC' as const
    }));
  }

  onGlobalSortCriteriaChange(criteria: SortOption[]): void {
    this.globalSortAsOptions = criteria;
    this.sortCriteria = this.toSortCriteria(criteria);
    this.syncLegacySort();
  }

  onOverrideSortCriteriaChange(index: number, criteria: SortOption[]): void {
    this.overrides[index].sortCriteriaAsOptions = criteria;
    this.overrides[index].sortCriteria = this.toSortCriteria(criteria);
    this.overrides[index].sort = criteria[0]?.field ?? 'title';
    this.overrides[index].sortDir = criteria[0]?.direction === SortDirection.ASCENDING ? 'ASC' : 'DESC';
  }

  private syncLegacySort(): void {
    if (this.sortCriteria.length > 0) {
      this.selectedSort = this.sortCriteria[0].field;
      this.selectedSortDir = this.sortCriteria[0].direction;
    }
  }

  // Visible sort fields management
  getSortFieldLabel(field: string): string {
    const key = this.sortOptions.find(opt => opt.field === field)?.translationKey;
    return key ? this.t.translate('settingsView.librarySort.' + key) : field;
  }

  get availableSortFieldsToAdd(): {label: string; value: string}[] {
    const used = new Set(this.visibleSortFields);
    return this.sortOptions
      .filter(opt => !used.has(opt.field))
      .map(opt => ({label: this.t.translate('settingsView.librarySort.' + opt.translationKey), value: opt.field}));
  }

  get sortFieldSelectionCountText(): string {
    return this.t.translate('settingsView.librarySort.sortFieldCount', {
      count: this.visibleSortFields.length,
      total: this.sortOptions.length
    });
  }

  onSortFieldDrop(event: CdkDragDrop<string[]>): void {
    moveItemInArray(this.visibleSortFields, event.previousIndex, event.currentIndex);
  }

  addSortField(): void {
    if (this.selectedAddSortField) {
      this.visibleSortFields.push(this.selectedAddSortField);
      this.selectedAddSortField = null;
      requestAnimationFrame(() => {
        const el = this.sortFieldList()?.nativeElement;
        if (el) el.scrollTop = el.scrollHeight;
      });
    }
  }

  removeSortField(index: number): void {
    if (this.visibleSortFields.length > this.minSortFields) {
      this.visibleSortFields.splice(index, 1);
    }
  }

  resetSortFieldsToDefaults(): void {
    this.visibleSortFields = [...DEFAULT_VISIBLE_SORT_FIELDS];
  }

  saveSettings(): void {
    if (!this.user) return;

    const prefs = structuredClone(this.user.userSettings.entityViewPreferences ?? {});

    prefs.global = {
      ...prefs.global,
      sortKey: this.selectedSort,
      sortDir: this.selectedSortDir,
      sortCriteria: [...this.sortCriteria],
      view: this.selectedView
    };

    prefs.overrides = this.overrides.map(o => {
      const existing = prefs.overrides?.find(p =>
        p.entityId === o.library && p.entityType === o.entityType
      )?.preferences;

      return {
        entityType: o.entityType,
        entityId: o.library,
        preferences: {
          sortKey: o.sortCriteria[0]?.field ?? o.sort,
          sortDir: o.sortCriteria[0]?.direction ?? o.sortDir,
          sortCriteria: [...o.sortCriteria],
          view: o.view,
          coverSize: existing?.coverSize ?? 1.0,
          seriesCollapsed: existing?.seriesCollapsed ?? false,
          overlayBookType: existing?.overlayBookType ?? true
        }
      };
    });

    this.userService.updateUserSetting(this.user.id, 'entityViewPreferences', prefs);
    this.userService.updateUserSetting(this.user.id, 'autoSaveMetadata', this.autoSaveMetadata);
    this.userService.updateUserSetting(this.user.id, 'visibleSortFields', this.visibleSortFields);

    this.messageService.add({
      severity: 'success',
      summary: this.t.translate('settingsView.librarySort.saveSuccess'),
      detail: this.t.translate('settingsView.librarySort.saveSuccessDetail')
    });
  }
}
