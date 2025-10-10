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
import {BookService} from '../../service/book.service';
import {Book} from '../../model/book.model';
import {FormsModule} from '@angular/forms';
import {Tooltip} from 'primeng/tooltip';
import {IconField} from 'primeng/iconfield';
import {InputIcon} from 'primeng/inputicon';
import {ActivatedRoute, Router} from '@angular/router';
import {Subscription} from 'rxjs';
import {ExternalDocLinkComponent} from '../../../shared/components/external-doc-link/external-doc-link.component';

interface MetadataItem {
  value: string;
  count: number;
  bookIds: number[];
  selected: boolean;
}

type MetadataType = 'authors' | 'categories' | 'moods' | 'tags' | 'series' | 'publishers' | 'languages';

interface TabConfig {
  type: MetadataType;
  label: string;
  placeholder: string;
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
    ExternalDocLinkComponent
  ],
  providers: [ConfirmationService],
  templateUrl: './metadata-manager.component.html',
  styleUrls: ['./metadata-manager.component.scss']
})
export class MetadataManagerComponent implements OnInit, OnDestroy {
  private bookService = inject(BookService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

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
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {tab: value},
      queryParamsHandling: 'merge'
    });
  }

  tabConfigs: TabConfig[] = [
    {type: 'authors', label: 'Author', placeholder: 'Search authors...', selectAllKey: 'selectAllAuthors', icon: 'pi-user'},
    {type: 'categories', label: 'Genre', placeholder: 'Search genres...', selectAllKey: 'selectAllCategories', icon: 'pi-tag'},
    {type: 'moods', label: 'Mood', placeholder: 'Search moods...', selectAllKey: 'selectAllMoods', icon: 'pi-heart'},
    {type: 'tags', label: 'Tag', placeholder: 'Search tags...', selectAllKey: 'selectAllTags', icon: 'pi-tags'},
    {type: 'series', label: 'Series', placeholder: 'Search series...', selectAllKey: 'selectAllSeries', icon: 'pi-book'},
    {type: 'publishers', label: 'Publisher', placeholder: 'Search publishers...', selectAllKey: 'selectAllPublishers', icon: 'pi-building'},
    {type: 'languages', label: 'Language', placeholder: 'Search languages...', selectAllKey: 'selectAllLanguages', icon: 'pi-globe'}
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
    });
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
        summary: 'Invalid Selection',
        detail: 'Please select at least 2 items to merge.'
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
          summary: 'Invalid Selection',
          detail: 'Please select at least 1 item to delete.'
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
        summary: 'Invalid Target',
        detail: 'Please enter a valid name.'
      });
      return;
    }

    const targetValues = this.renameTarget.split(',').map(v => v.trim()).filter(v => v.length > 0);

    if (targetValues.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Invalid Target',
        detail: 'Please enter at least one valid target value.'
      });
      return;
    }

    if (this.isSingleValueField(this.currentMergeType) && targetValues.length > 1) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Invalid Target',
        detail: `Each book can only have one ${this.currentMergeType.slice(0, -1)}. Please enter only one value.`
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
    this.bookService.consolidateMetadata(this.currentMergeType, targetValues, [oldValue]).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: `${action === 'renamed' ? 'Rename' : 'Split'} Successful`,
          detail: `Successfully ${action} "${oldValue}" ${action === 'renamed' ? 'to' : 'into'} ${resultText}. ${affectedBooks} book${affectedBooks > 1 ? 's' : ''} updated.`,
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
          summary: `${action === 'renamed' ? 'Rename' : 'Split'} Failed`,
          detail: error?.error?.message || `An error occurred during ${action === 'renamed' ? 'rename' : 'split'}.`
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
        summary: 'Invalid Target',
        detail: 'Please enter a target value for merging.'
      });
      return;
    }

    const targetValues = this.mergeTarget.split(',').map(v => v.trim()).filter(v => v.length > 0);

    if (targetValues.length === 0) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Invalid Target',
        detail: 'Please enter at least one valid target value.'
      });
      return;
    }

    if (this.isSingleValueField(this.currentMergeType) && targetValues.length > 1) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Invalid Target',
        detail: `Each book can only have one ${this.currentMergeType.slice(0, -1)}. Please enter only one target value to standardize to.`
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
    const operationPast = targetValues.length === 1 ? 'merged' : 'merged/split';

    this.loading = true;
    this.mergingInProgress = true;
    this.bookService.consolidateMetadata(this.currentMergeType, targetValues, valuesToMerge).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: `${operation === 'merge' ? 'Merge' : 'Merge/Split'} Successful`,
          detail: `Successfully ${operationPast} ${selected.length} ${this.currentMergeType} into ${targetValues.length} value(s). ${affectedBooks} book${affectedBooks > 1 ? 's' : ''} updated.`,
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
          summary: `${operation === 'merge' ? 'Merge' : 'Merge/Split'} Failed`,
          detail: error?.error?.message || `An error occurred during ${operation}.`
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
        summary: 'Invalid Selection',
        detail: 'Please select at least one item to delete.'
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
    this.bookService.deleteMetadata(this.currentMergeType, valuesToDelete).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Delete Successful',
          detail: `Successfully deleted ${itemCount} ${this.currentMergeType.slice(0, -1)}${itemCount > 1 ? 's' : ''}. ${affectedBooks} book${affectedBooks > 1 ? 's' : ''} updated.`,
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
          summary: 'Delete Failed',
          detail: error?.error?.message || 'An error occurred during deletion.'
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

  filterGlobal(event: any, dt: any) {
    dt.filterGlobal(event.target.value, 'contains');
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
        filter: `${filterKey}:${filterValue}`
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
