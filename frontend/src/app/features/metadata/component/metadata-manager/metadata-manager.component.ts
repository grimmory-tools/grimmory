import {Component, computed, inject, linkedSignal, OnDestroy, OnInit, signal} from '@angular/core';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from '@openng/optimus-ui/tabs';
import {TableModule} from '@openng/optimus-ui/table';
import {Button} from '@openng/optimus-ui/button';
import {Checkbox} from '@openng/optimus-ui/checkbox';
import {InputText} from '@openng/optimus-ui/inputtext';
import {Dialog} from '@openng/optimus-ui/dialog';
import {ConfirmationService, MessageService} from '@openng/optimus-ui/api';
import {injectQuery, keepPreviousData} from '@tanstack/angular-query-experimental';
import {PageTitleService} from "../../../../shared/service/page-title.service";
import {BookMetadataManageService, MetadataSelection} from '../../../book/service/book-metadata-manage.service';
import {BookQueryService} from '../../../book/data/book-query.service';
import {BookQueryFacetKey, EMPTY_FACET_SELECTION} from '../../../book/data/book-query-params';
import {withBrowseFacetValues} from '../../../../shared/browse/facets';
import {debouncedSignal} from '../../../../shared/util/debounced-signal';
import {SEARCH_DEBOUNCE_MS} from '../../../../shared/util/search-terms';
import {FormsModule} from '@angular/forms';
import {Tooltip} from '@openng/optimus-ui/tooltip';
import {IconField} from '@openng/optimus-ui/iconfield';
import {InputIcon} from '@openng/optimus-ui/inputicon';
import {ActivatedRoute, Router} from '@angular/router';
import {Subscription} from 'rxjs';
import {ExternalDocLinkComponent} from '../../../../shared/components/external-doc-link/external-doc-link.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

interface MetadataItem {
  value: string;
  count: number;
}

type MetadataType = 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages';

interface TabConfig {
  type: MetadataType;
  labelKey: string;
  labelPluralKey: string;
  placeholderKey: string;
  icon: string;
}

type RowSelection =
  | {kind: 'values'; values: ReadonlyMap<string, number>}
  | {kind: 'matching'; excluded: ReadonlySet<string>};

const FACET_KEYS: Record<MetadataType, BookQueryFacetKey> = {
  authors: 'author',
  categories: 'genre',
  moods: 'mood',
  tags: 'tag',
  series: 'series',
  publishers: 'publisher',
  languages: 'language'
};
const NO_SELECTION: RowSelection = {kind: 'values', values: new Map()};
const NO_FILTERS = {facets: EMPTY_FACET_SELECTION};
const PAGE_SIZE = 50;

@Component({
  selector: 'app-metadata-manager',
  standalone: true,
  imports: [
    FormsModule,
    Tabs,
    TableModule,
    Button,
    Checkbox,
    InputText,
    Dialog,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    Tooltip,
    IconField,
    InputIcon,
    ExternalDocLinkComponent,
    TranslocoDirective,
    TranslocoPipe
  ],
  providers: [ConfirmationService],
  templateUrl: './metadata-manager.component.html',
  styleUrls: ['./metadata-manager.component.scss']
})
export class MetadataManagerComponent implements OnInit, OnDestroy {
  private readonly bookQuery = inject(BookQueryService);
  private readonly bookMetadataManageService = inject(BookMetadataManageService);
  private readonly messageService = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  private routeSub!: Subscription;
  readonly loading = signal(false);
  readonly mergingInProgress = signal(false);
  readonly deletingInProgress = signal(false);
  readonly showMergeDialog = signal(false);
  showRenameDialog = false;
  readonly showDeleteDialog = signal(false);
  mergeTarget = '';
  renameTarget = '';
  currentMergeType: MetadataType = 'authors';
  currentRenameItem: MetadataItem | null = null;
  currentDeleteItem: MetadataItem | null = null;

  private readonly validTabs: MetadataType[] = ['authors', 'categories', 'moods', 'tags', 'series', 'publishers', 'languages'];
  private readonly _activeTab = signal<MetadataType>('authors');

  get activeTab(): MetadataType {
    return this._activeTab();
  }

  set activeTab(value: MetadataType) {
    this._activeTab.set(value);

    this.updatePageTitle();

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {tab: value},
      queryParamsHandling: 'merge'
    });
  }

  tabConfigs: TabConfig[] = [
    {type: 'authors', labelKey: 'tabs.author', labelPluralKey: 'tabs.authors', placeholderKey: 'placeholders.searchAuthors', icon: 'pi-user'},
    {type: 'categories', labelKey: 'tabs.genre', labelPluralKey: 'tabs.genres', placeholderKey: 'placeholders.searchGenres', icon: 'pi-tag'},
    {type: 'moods', labelKey: 'tabs.mood', labelPluralKey: 'tabs.moods', placeholderKey: 'placeholders.searchMoods', icon: 'pi-heart'},
    {type: 'tags', labelKey: 'tabs.tag', labelPluralKey: 'tabs.tags', placeholderKey: 'placeholders.searchTags', icon: 'pi-tags'},
    {type: 'series', labelKey: 'tabs.series', labelPluralKey: 'tabs.seriesPlural', placeholderKey: 'placeholders.searchSeries', icon: 'pi-book'},
    {type: 'publishers', labelKey: 'tabs.publisher', labelPluralKey: 'tabs.publishers', placeholderKey: 'placeholders.searchPublishers', icon: 'pi-building'},
    {type: 'languages', labelKey: 'tabs.language', labelPluralKey: 'tabs.languages', placeholderKey: 'placeholders.searchLanguages', icon: 'pi-globe'}
  ];

  readonly searchInput = linkedSignal({source: this._activeTab, computation: () => ''});
  private readonly debouncedSearch = debouncedSignal(
    computed(() => ({tab: this._activeTab(), search: this.searchInput().trim()})),
    SEARCH_DEBOUNCE_MS,
  );
  readonly search = computed(() => {
    const debounced = this.debouncedSearch();
    return debounced.tab === this._activeTab() ? debounced.search : '';
  });
  private readonly scope = computed(
    () => ({tab: this._activeTab(), search: this.search()}),
    {equal: (a, b) => a.tab === b.tab && a.search === b.search},
  );

  readonly page = linkedSignal({source: this.scope, computation: () => 0});
  readonly selection = linkedSignal({source: this.scope, computation: (): RowSelection => NO_SELECTION});

  private readonly rowsQuery = injectQuery(() => ({
    ...this.bookQuery.facet(FACET_KEYS[this._activeTab()], NO_FILTERS, this.search(), this.page(), PAGE_SIZE),
    placeholderData: keepPreviousData,
  }));
  readonly rows = computed(() => this.rowsQuery.data()?.values ?? []);
  readonly hasNextPage = computed(() => !this.rowsQuery.isPlaceholderData() && this.rowsQuery.data()?.complete === false);
  readonly rowsLoading = this.rowsQuery.isFetching;

  private readonly affectedBooksQuery = injectQuery(() => {
    const values = this.getSelectedItems().map(item => item.value);
    const facets = withBrowseFacetValues(EMPTY_FACET_SELECTION, FACET_KEYS[this._activeTab()], {include: values, exclude: []});
    return {
      ...this.bookQuery.ids({facets, sort: []}),
      enabled: values.length > 0 && (this.showMergeDialog() || this.showDeleteDialog()),
    };
  });
  readonly affectedBooks = computed(() => this.affectedBooksQuery.data()?.length ?? null);

  readonly allSelected = computed(() => {
    const selection = this.selection();
    return selection.kind === 'matching' && selection.excluded.size === 0;
  });
  readonly hasSelection = computed(() => {
    const selection = this.selection();
    return selection.kind === 'matching' || selection.values.size > 0;
  });
  readonly canMerge = computed(() => {
    const selection = this.selection();
    return selection.kind === 'matching' || selection.values.size >= 2;
  });
  readonly selectionLabel = computed(() => {
    const selection = this.selection();
    const type = this.getTypeLabel(this._activeTab(), true);
    const except = selection.kind === 'matching' ? selection.excluded.size : 0;
    const search = this.search();
    let key: string;
    if (search === '') {
      key = except > 0 ? 'allValuesExcept' : 'allValues';
    } else {
      key = except > 0 ? 'allMatchingExcept' : 'allMatching';
    }
    return this.t.translate('metadata.manager.' + key, {type, search, count: except});
  });

  ngOnInit() {
    this.routeSub = this.route.queryParams.subscribe(params => {
      const tabParam = params['tab'] as MetadataType;
      if (this.validTabs.includes(tabParam)) {
        this._activeTab.set(tabParam);
      } else {
        this._activeTab.set('authors');
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {tab: this._activeTab()},
          queryParamsHandling: 'merge',
          replaceUrl: true
        });
      }
      this.updatePageTitle();
    });
  }

  updatePageTitle() {
    const currentTab = this.tabConfigs.find((tab) => tab.type === this._activeTab());
    const label = currentTab ? this.t.translate('metadata.manager.' + currentTab.labelKey) : this._activeTab();
    this.pageTitle.setPageTitle(`${this.t.translate('metadata.manager.title')}: ${label}`);
  }

  ngOnDestroy(): void {
    this.routeSub.unsubscribe();
  }

  getSelectedItems(): MetadataItem[] {
    const selection = this.selection();
    return selection.kind === 'values'
      ? [...selection.values].map(([value, count]) => ({value, count}))
      : [];
  }

  openMergeDialog(type: MetadataType) {
    const selection = this.selection();
    if (!this.canMerge()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidSelectionSummary'),
        detail: this.t.translate('metadata.manager.toast.mergeMinDetail')
      });
      return;
    }

    this.currentMergeType = type;
    this.mergeTarget = selection.kind === 'values'
      ? this.getSelectedItems()[0].value
      : this.rows().find(row => !selection.excluded.has(row.value))?.value ?? '';
    this.showMergeDialog.set(true);
  }

  openRenameDialog(type: MetadataType, item: MetadataItem) {
    this.currentMergeType = type;
    this.currentRenameItem = item;
    this.renameTarget = item.value;
    this.showRenameDialog = true;
  }

  openDeleteDialog(type: MetadataType, item?: MetadataItem) {
    if (item) {
      this.currentMergeType = type;
      this.currentDeleteItem = item;
      this.showDeleteDialog.set(true);
    } else {
      if (!this.hasSelection()) {
        this.messageService.add({
          severity: 'warn',
          summary: this.t.translate('metadata.manager.toast.invalidSelectionSummary'),
          detail: this.t.translate('metadata.manager.toast.deleteMinDetail')
        });
        return;
      }
      this.currentMergeType = type;
      this.currentDeleteItem = null;
      this.showDeleteDialog.set(true);
    }
  }

  confirmRename() {
    if (!this.currentRenameItem || !this.renameTarget.trim()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidTargetSummary'),
        detail: this.t.translate('metadata.manager.toast.enterValidName')
      });
      return;
    }

    const targetValues = this.renameTarget.split(',').map(v => v.trim()).filter(v => v.length > 0);

    if (targetValues.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidTargetSummary'),
        detail: this.t.translate('metadata.manager.toast.enterValidTarget')
      });
      return;
    }

    if (this.isSingleValueField(this.currentMergeType) && targetValues.length > 1) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidTargetSummary'),
        detail: this.t.translate('metadata.manager.toast.singleValueOnly', {singular: this.getTypeLabel(this.currentMergeType, false)})
      });
      return;
    }

    if (targetValues.length === 1 && targetValues[0] === this.currentRenameItem.value) {
      this.showRenameDialog = false;
      return;
    }

    this.performRename(targetValues);
  }

  private performRename(targetValues: string[]) {
    if (!this.currentRenameItem) return;

    const oldValue = this.currentRenameItem.value;
    const affectedBooks = this.currentRenameItem.count;
    const action = targetValues.length === 1 ? 'renamed' : 'split';
    const resultText = targetValues.length === 1
      ? `"${targetValues[0]}"`
      : `${targetValues.length} values`;

    this.loading.set(true);
    this.mergingInProgress.set(true);
    this.bookMetadataManageService.consolidateMetadata(this.currentMergeType, targetValues, {type: 'values', values: [oldValue]}).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: action === 'renamed'
            ? this.t.translate('metadata.manager.toast.renameSuccessfulSummary')
            : this.t.translate('metadata.manager.toast.splitSuccessfulSummary'),
          detail: action === 'renamed'
            ? this.t.translate('metadata.manager.toast.renameSuccessfulDetail', {oldValue, resultText, count: affectedBooks})
            : this.t.translate('metadata.manager.toast.splitSuccessfulDetail', {oldValue, resultText, count: affectedBooks}),
          life: 5000
        });
        this.showRenameDialog = false;
        this.currentRenameItem = null;
        this.renameTarget = '';
        this.clearSelection();
        this.page.set(0);
        this.mergingInProgress.set(false);
        this.loading.set(false);
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: action === 'renamed'
            ? this.t.translate('metadata.manager.toast.renameFailedSummary')
            : this.t.translate('metadata.manager.toast.splitFailedSummary'),
          detail: error?.error?.message || (action === 'renamed'
            ? this.t.translate('metadata.manager.toast.renameFailedDetail')
            : this.t.translate('metadata.manager.toast.splitFailedDetail'))
        });
        this.loading.set(false);
        this.mergingInProgress.set(false);
      }
    });
  }

  confirmMerge() {
    if (!this.mergeTarget.trim()) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidTargetSummary'),
        detail: this.t.translate('metadata.manager.toast.enterMergeTarget')
      });
      return;
    }

    const targetValues = this.mergeTarget.split(',').map(v => v.trim()).filter(v => v.length > 0);

    if (targetValues.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidTargetSummary'),
        detail: this.t.translate('metadata.manager.toast.enterValidTarget')
      });
      return;
    }

    if (this.isSingleValueField(this.currentMergeType) && targetValues.length > 1) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidTargetSummary'),
        detail: this.t.translate('metadata.manager.toast.singleMergeValueOnly', {singular: this.getTypeLabel(this.currentMergeType, false)})
      });
      return;
    }

    this.performMerge(targetValues);
  }

  private performMerge(targetValues: string[]) {
    const selected = this.getSelectedItems();
    const affectedBooks = this.affectedBooks();
    const operation = targetValues.length === 1 ? 'merge' : 'merge/split';
    let detail: string | undefined;
    if (affectedBooks !== null) {
      detail = operation === 'merge'
        ? this.t.translate('metadata.manager.toast.mergeSuccessfulDetail', {selectedCount: selected.length, type: this.getTypeLabel(this.currentMergeType, true), targetCount: targetValues.length, bookCount: affectedBooks})
        : this.t.translate('metadata.manager.toast.mergeSplitSuccessfulDetail', {selectedCount: selected.length, type: this.getTypeLabel(this.currentMergeType, true), targetCount: targetValues.length, bookCount: affectedBooks});
    }

    this.loading.set(true);
    this.mergingInProgress.set(true);
    this.bookMetadataManageService.consolidateMetadata(this.currentMergeType, targetValues, this.requestSelection()).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: operation === 'merge'
            ? this.t.translate('metadata.manager.toast.mergeSuccessfulSummary')
            : this.t.translate('metadata.manager.toast.mergeSplitSuccessfulSummary'),
          detail,
          life: 5000
        });
        this.showMergeDialog.set(false);
        this.mergeTarget = '';
        this.clearSelection();
        this.page.set(0);
        this.mergingInProgress.set(false);
        this.loading.set(false);
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: operation === 'merge'
            ? this.t.translate('metadata.manager.toast.mergeFailedSummary')
            : this.t.translate('metadata.manager.toast.mergeSplitFailedSummary'),
          detail: error?.error?.message || (operation === 'merge'
            ? this.t.translate('metadata.manager.toast.mergeFailedDetail')
            : this.t.translate('metadata.manager.toast.mergeSplitFailedDetail'))
        });
        this.loading.set(false);
        this.mergingInProgress.set(false);
      }
    });
  }

  confirmDelete() {
    const selection: MetadataSelection = this.currentDeleteItem
      ? {type: 'values', values: [this.currentDeleteItem.value]}
      : this.requestSelection();

    if (selection.type === 'values' && selection.values.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidSelectionSummary'),
        detail: this.t.translate('metadata.manager.toast.deleteSelectMinDetail')
      });
      return;
    }

    this.performDelete(selection);
  }

  private performDelete(selection: MetadataSelection) {
    const affectedBooks = this.currentDeleteItem
      ? this.currentDeleteItem.count
      : this.affectedBooks();

    this.loading.set(true);
    this.deletingInProgress.set(true);
    this.bookMetadataManageService.deleteMetadata(this.currentMergeType, selection).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.manager.toast.deleteSuccessfulSummary'),
          detail: selection.type === 'values' && affectedBooks !== null
            ? this.t.translate('metadata.manager.toast.deleteSuccessfulDetail', {count: selection.values.length, type: selection.values.length > 1 ? this.getTypeLabel(this.currentMergeType, true) : this.getTypeLabel(this.currentMergeType, false), bookCount: affectedBooks})
            : undefined,
          life: 5000
        });
        this.showDeleteDialog.set(false);
        this.currentDeleteItem = null;
        this.clearSelection();
        this.page.set(0);
        this.deletingInProgress.set(false);
        this.loading.set(false);
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.manager.toast.deleteFailedSummary'),
          detail: error?.error?.message || this.t.translate('metadata.manager.toast.deleteFailedDetail')
        });
        this.loading.set(false);
        this.deletingInProgress.set(false);
      }
    });
  }

  isSelected(item: MetadataItem): boolean {
    const selection = this.selection();
    return selection.kind === 'values' ? selection.values.has(item.value) : !selection.excluded.has(item.value);
  }

  setRowSelected(item: MetadataItem, selected: boolean) {
    const selection = this.selection();
    if (selection.kind === 'values') {
      const values = new Map(selection.values);
      if (selected) {
        values.set(item.value, item.count);
      } else {
        values.delete(item.value);
      }
      this.selection.set({kind: 'values', values});
    } else {
      const excluded = new Set(selection.excluded);
      if (selected) {
        excluded.delete(item.value);
      } else {
        excluded.add(item.value);
      }
      this.selection.set({kind: 'matching', excluded});
    }
  }

  setAllSelected(selected: boolean) {
    this.selection.set(selected ? {kind: 'matching', excluded: new Set()} : NO_SELECTION);
  }

  clearSelection() {
    this.selection.set(NO_SELECTION);
  }

  selectSimilar(value: string) {
    this.searchInput.set(value);
  }

  onMetadataClick(type: MetadataType, value: string): void {
    const filterKeyMap: Record<MetadataType, string> = {
      authors: 'author',
      categories: 'genre',
      moods: 'mood',
      tags: 'tag',
      series: 'series',
      publishers: 'publisher',
      languages: 'language'
    };

    this.navigateToFilteredBooks(filterKeyMap[type], value);
  }

  navigateToFilteredBooks(filterKey: string, filterValue: string): void {
    this.router.navigate(['/all-books'], {
      queryParams: {
        facet: `${filterKey}:${filterValue}`,
      }
    });
  }

  protected getTypeLabel(type: MetadataType, plural: boolean): string {
    const tab = this.tabConfigs.find(tc => tc.type === type);
    if (!tab) return type;
    return this.t.translate('metadata.manager.' + (plural ? tab.labelPluralKey : tab.labelKey));
  }

  protected isSingleValueField(type: MetadataType): boolean {
    return type === 'series' || type === 'publishers' || type === 'languages';
  }

  private requestSelection(): MetadataSelection {
    const selection = this.selection();
    return selection.kind === 'values'
      ? {type: 'values', values: [...selection.values.keys()]}
      : {type: 'matching', search: this.search(), excludedValues: [...selection.excluded]};
  }
}
