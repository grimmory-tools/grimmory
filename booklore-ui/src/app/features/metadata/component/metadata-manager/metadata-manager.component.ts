import {Component, inject, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {CheckboxModule} from 'primeng/checkbox';
import {InputTextModule} from 'primeng/inputtext';
import {DialogModule} from 'primeng/dialog';
import {ConfirmDialogModule} from 'primeng/confirmdialog';
import {ConfirmationService, MessageService} from 'primeng/api';
import {PageTitleService} from "../../../../shared/service/page-title.service";
import {BookService} from '../../../book/service/book.service';
import {BookMetadataManageService} from '../../../book/service/book-metadata-manage.service';
import {Book} from '../../../book/model/book.model';
import {FormsModule} from '@angular/forms';
import {Tooltip} from 'primeng/tooltip';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {ActivatedRoute, Router} from '@angular/router';
import {Subscription} from 'rxjs';
import {ExternalDocLinkComponent} from '../../../../shared/components/external-doc-link/external-doc-link.component';
import {TranslocoDirective, TranslocoPipe, TranslocoService} from '@jsverse/transloco';

interface MetadataItem {
  value: string;
  count: number;
  bookIds: number[];
  selected: boolean;
}

type MetadataType = 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages';

interface TabConfig {
  type: MetadataType;
  labelKey: string;
  placeholderKey: string;
  selectAllKey: 'selectAllAuthors' | 'selectAllCategories' | 'selectAllMoods' | 'selectAllTags' | 'selectAllSeries' | 'selectAllPublishers' | 'selectAllLanguages';
  icon: string;
}

@Component({
  selector: 'app-metadata-manager',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    Tabs,
    TableModule,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    DialogModule,
    ConfirmDialogModule,
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
  private bookService = inject(BookService);
  private bookMetadataManageService = inject(BookMetadataManageService);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private pageTitle = inject(PageTitleService);
  private readonly t = inject(TranslocoService);

  private routeSub!: Subscription;

  authors: MetadataItem[] = [];
  categories: MetadataItem[] = [];
  moods: MetadataItem[] = [];
  tags: MetadataItem[] = [];
  series: MetadataItem[] = [];
  publishers: MetadataItem[] = [];
  languages: MetadataItem[] = [];

  selectAllAuthors = false;
  selectAllCategories = false;
  selectAllMoods = false;
  selectAllTags = false;
  selectAllSeries = false;
  selectAllPublishers = false;
  selectAllLanguages = false;

  loading = false;
  mergingInProgress = false;
  deletingInProgress = false;
  showMergeDialog = false;
  showRenameDialog = false;
  showDeleteDialog = false;
  mergeTarget = '';
  renameTarget = '';
  currentMergeType: MetadataType = 'authors';
  currentRenameItem: MetadataItem | null = null;
  currentDeleteItem: MetadataItem | null = null;

  private validTabs: MetadataType[] = ['authors', 'categories', 'moods', 'tags', 'series', 'publishers', 'languages'];
  private _activeTab: MetadataType = 'authors';

  get activeTab(): MetadataType {
    return this._activeTab;
  }

  set activeTab(value: MetadataType) {
    this._activeTab = value;

    this.updatePageTitle();

    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {tab: value},
      queryParamsHandling: 'merge'
    });
  }

  tabConfigs: TabConfig[] = [
    {type: 'authors', labelKey: 'tabs.author', placeholderKey: 'placeholders.searchAuthors', selectAllKey: 'selectAllAuthors', icon: 'pi-user'},
    {type: 'categories', labelKey: 'tabs.genre', placeholderKey: 'placeholders.searchGenres', selectAllKey: 'selectAllCategories', icon: 'pi-tag'},
    {type: 'moods', labelKey: 'tabs.mood', placeholderKey: 'placeholders.searchMoods', selectAllKey: 'selectAllMoods', icon: 'pi-heart'},
    {type: 'tags', labelKey: 'tabs.tag', placeholderKey: 'placeholders.searchTags', selectAllKey: 'selectAllTags', icon: 'pi-tags'},
    {type: 'series', labelKey: 'tabs.series', placeholderKey: 'placeholders.searchSeries', selectAllKey: 'selectAllSeries', icon: 'pi-book'},
    {type: 'publishers', labelKey: 'tabs.publisher', placeholderKey: 'placeholders.searchPublishers', selectAllKey: 'selectAllPublishers', icon: 'pi-building'},
    {type: 'languages', labelKey: 'tabs.language', placeholderKey: 'placeholders.searchLanguages', selectAllKey: 'selectAllLanguages', icon: 'pi-globe'}
  ];

  ngOnInit() {
    this.loadMetadata();
    this.routeSub = this.route.queryParams.subscribe(params => {
      const tabParam = params['tab'] as MetadataType;
      if (this.validTabs.includes(tabParam)) {
        this._activeTab = tabParam;
      } else {
        this._activeTab = 'authors';
        this.router.navigate([], {
          relativeTo: this.route,
          queryParams: {tab: this._activeTab},
          queryParamsHandling: 'merge',
          replaceUrl: true
        });
      }
      this.updatePageTitle();
    });
  }

  updatePageTitle() {
    const currentTab = this.tabConfigs.find((tab) => tab.type === this._activeTab);
    const label = currentTab ? this.t.translate('metadata.manager.' + currentTab.labelKey) : this._activeTab;
    this.pageTitle.setPageTitle(`${this.t.translate('metadata.manager.title')}: ${label}`);
  }

  ngOnDestroy(): void {
    this.routeSub.unsubscribe();
  }

  loadMetadata() {
    this.loading = true;
    this.bookService.bookState$.subscribe(state => {
      if (state.loaded && state.books) {
        this.extractMetadata(state.books);
        this.loading = false;
      }
    });
  }

  private extractMetadata(books: Book[]) {
    const authorsMap = new Map<string, Set<number>>();
    const categoriesMap = new Map<string, Set<number>>();
    const moodsMap = new Map<string, Set<number>>();
    const tagsMap = new Map<string, Set<number>>();
    const seriesMap = new Map<string, Set<number>>();
    const publishersMap = new Map<string, Set<number>>();
    const languagesMap = new Map<string, Set<number>>();

    books.forEach(book => {
      if (book.metadata) {
        this.addToMap(authorsMap, book.metadata.authors, book.id);
        this.addToMap(categoriesMap, book.metadata.categories, book.id);
        this.addToMap(moodsMap, book.metadata.moods, book.id);
        this.addToMap(tagsMap, book.metadata.tags, book.id);
        if (book.metadata.seriesName) {
          this.addToMap(seriesMap, [book.metadata.seriesName], book.id);
        }
        if (book.metadata.publisher) {
          this.addToMap(publishersMap, [book.metadata.publisher], book.id);
        }
        if (book.metadata.language) {
          this.addToMap(languagesMap, [book.metadata.language], book.id);
        }
      }
    });

    this.authors = this.mapToItems(authorsMap);
    this.categories = this.mapToItems(categoriesMap);
    this.moods = this.mapToItems(moodsMap);
    this.tags = this.mapToItems(tagsMap);
    this.series = this.mapToItems(seriesMap);
    this.publishers = this.mapToItems(publishersMap);
    this.languages = this.mapToItems(languagesMap);
  }

  private addToMap(map: Map<string, Set<number>>, values: string[] | undefined, bookId: number) {
    if (!values) return;
    values.forEach(value => {
      if (!map.has(value)) {
        map.set(value, new Set());
      }
      map.get(value)!.add(bookId);
    });
  }

  private mapToItems(map: Map<string, Set<number>>): MetadataItem[] {
    return Array.from(map.entries())
      .map(([value, bookIds]) => ({
        value,
        count: bookIds.size,
        bookIds: Array.from(bookIds),
        selected: false
      }))
      .sort((a, b) => b.count - a.count);
  }

  getSelectedItems(type: MetadataType): MetadataItem[] {
    return this[type].filter(item => item.selected);
  }

  openMergeDialog(type: MetadataType) {
    const selected = this.getSelectedItems(type);
    if (selected.length < 2) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidSelectionSummary'),
        detail: this.t.translate('metadata.manager.toast.mergeMinDetail')
      });
      return;
    }

    this.currentMergeType = type;
    this.mergeTarget = selected[0].value;
    this.showMergeDialog = true;
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
      this.showDeleteDialog = true;
    } else {
      const selected = this.getSelectedItems(type);
      if (selected.length === 0) {
        this.messageService.add({
          severity: 'warn',
          summary: this.t.translate('metadata.manager.toast.invalidSelectionSummary'),
          detail: this.t.translate('metadata.manager.toast.deleteMinDetail')
        });
        return;
      }
      this.currentMergeType = type;
      this.currentDeleteItem = null;
      this.showDeleteDialog = true;
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
        detail: this.t.translate('metadata.manager.toast.singleValueOnly', {singular: this.currentMergeType.slice(0, -1)})
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

    this.loading = true;
    this.mergingInProgress = true;
    this.bookMetadataManageService.consolidateMetadata(this.currentMergeType, targetValues, [oldValue]).subscribe({
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
        this.mergingInProgress = false;
        this.loading = false;
        this.loadMetadata();
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
        this.loading = false;
        this.mergingInProgress = false;
      }
    });
  }

  confirmMerge() {
    const selected = this.getSelectedItems(this.currentMergeType);
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
        detail: this.t.translate('metadata.manager.toast.singleMergeValueOnly', {singular: this.currentMergeType.slice(0, -1)})
      });
      return;
    }

    this.performMerge(targetValues);
  }

  private performMerge(targetValues: string[]) {
    const selected = this.getSelectedItems(this.currentMergeType);
    const valuesToMerge = selected.map(s => s.value);
    const affectedBooks = this.getTotalAffectedBooks(selected);
    const operation = targetValues.length === 1 ? 'merge' : 'merge/split';

    this.loading = true;
    this.mergingInProgress = true;
    this.bookMetadataManageService.consolidateMetadata(this.currentMergeType, targetValues, valuesToMerge).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: operation === 'merge'
            ? this.t.translate('metadata.manager.toast.mergeSuccessfulSummary')
            : this.t.translate('metadata.manager.toast.mergeSplitSuccessfulSummary'),
          detail: operation === 'merge'
            ? this.t.translate('metadata.manager.toast.mergeSuccessfulDetail', {selectedCount: selected.length, type: this.currentMergeType, targetCount: targetValues.length, bookCount: affectedBooks})
            : this.t.translate('metadata.manager.toast.mergeSplitSuccessfulDetail', {selectedCount: selected.length, type: this.currentMergeType, targetCount: targetValues.length, bookCount: affectedBooks}),
          life: 5000
        });
        this.showMergeDialog = false;
        this.mergeTarget = '';
        this.clearSelection(this.currentMergeType);
        this.mergingInProgress = false;
        this.loading = false;
        this.loadMetadata();
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
        this.loading = false;
        this.mergingInProgress = false;
      }
    });
  }

  confirmDelete() {
    const itemsToDelete = this.currentDeleteItem
      ? [this.currentDeleteItem.value]
      : this.getSelectedItems(this.currentMergeType).map(item => item.value);

    if (itemsToDelete.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: this.t.translate('metadata.manager.toast.invalidSelectionSummary'),
        detail: this.t.translate('metadata.manager.toast.deleteSelectMinDetail')
      });
      return;
    }

    this.performDelete(itemsToDelete);
  }

  private performDelete(valuesToDelete: string[]) {
    const itemCount = valuesToDelete.length;
    const affectedBooks = this.currentDeleteItem
      ? this.currentDeleteItem.count
      : this.getTotalAffectedBooks(this.getSelectedItems(this.currentMergeType));

    this.loading = true;
    this.deletingInProgress = true;
    this.bookMetadataManageService.deleteMetadata(this.currentMergeType, valuesToDelete).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: this.t.translate('metadata.manager.toast.deleteSuccessfulSummary'),
          detail: this.t.translate('metadata.manager.toast.deleteSuccessfulDetail', {count: itemCount, type: this.currentMergeType.slice(0, -1) + (itemCount > 1 ? 's' : ''), bookCount: affectedBooks}),
          life: 5000
        });
        this.showDeleteDialog = false;
        this.currentDeleteItem = null;
        this.clearSelection(this.currentMergeType);
        this.deletingInProgress = false;
        this.loading = false;
        this.loadMetadata();
      },
      error: (error) => {
        this.messageService.add({
          severity: 'error',
          summary: this.t.translate('metadata.manager.toast.deleteFailedSummary'),
          detail: error?.error?.message || this.t.translate('metadata.manager.toast.deleteFailedDetail')
        });
        this.loading = false;
        this.deletingInProgress = false;
      }
    });
  }

  getMetadataItems(type: MetadataType): MetadataItem[] {
    return this[type];
  }

  getSelectAll(key: TabConfig['selectAllKey']): boolean {
    return this[key];
  }

  setSelectAll(key: TabConfig['selectAllKey'], value: boolean) {
    this[key] = value;
  }

  toggleAll(type: MetadataType, checked: boolean, filteredItems?: MetadataItem[]) {
    const itemsToToggle = filteredItems || this[type];
    itemsToToggle.forEach(item => item.selected = checked);
    this.updateSelectAllState(type);
  }

  private updateSelectAllState(type: MetadataType) {
    const allItems = this[type];
    const allSelected = allItems.length > 0 && allItems.every(item => item.selected);

    switch (type) {
      case 'authors':
        this.selectAllAuthors = allSelected;
        break;
      case 'categories':
        this.selectAllCategories = allSelected;
        break;
      case 'moods':
        this.selectAllMoods = allSelected;
        break;
      case 'tags':
        this.selectAllTags = allSelected;
        break;
      case 'series':
        this.selectAllSeries = allSelected;
        break;
      case 'publishers':
        this.selectAllPublishers = allSelected;
        break;
      case 'languages':
        this.selectAllLanguages = allSelected;
        break;
    }
  }

  onRowSelect(type: MetadataType) {
    this.updateSelectAllState(type);
  }

  clearSelection(type: MetadataType) {
    this[type].forEach(item => item.selected = false);
    switch (type) {
      case 'authors':
        this.selectAllAuthors = false;
        break;
      case 'categories':
        this.selectAllCategories = false;
        break;
      case 'moods':
        this.selectAllMoods = false;
        break;
      case 'tags':
        this.selectAllTags = false;
        break;
      case 'series':
        this.selectAllSeries = false;
        break;
      case 'publishers':
        this.selectAllPublishers = false;
        break;
      case 'languages':
        this.selectAllLanguages = false;
        break;
    }
  }

  selectSimilar(type: MetadataType, value: string) {
    const normalized = value.toLowerCase().trim();
    this[type].forEach(item => {
      const itemNormalized = item.value.toLowerCase().trim();
      item.selected = itemNormalized.includes(normalized) || normalized.includes(itemNormalized);
    });
  }

  filterGlobal(event: Event, dt: { filterGlobal: (value: string, matchMode: string) => void }): void {
    const target = event.target as HTMLInputElement;
    if (target) {
      dt.filterGlobal(target.value, 'contains');
    }
  }

  onMetadataClick(type: MetadataType, value: string): void {
    const filterKeyMap: Record<MetadataType, string> = {
      authors: 'author',
      categories: 'category',
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
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: `${filterKey}:${encodeURIComponent(filterValue)}`
      }
    });
  }

  protected isSingleValueField(type: MetadataType): boolean {
    return type === 'series' || type === 'publishers' || type === 'languages';
  }

  protected getTotalAffectedBooks(items: MetadataItem[]): number {
    const uniqueBookIds = new Set<number>();
    items.forEach(item => {
      item.bookIds.forEach(bookId => uniqueBookIds.add(bookId));
    });
    return uniqueBookIds.size;
  }
}
