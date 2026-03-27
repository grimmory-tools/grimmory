import {SortDirection, SortOption} from '../../../model/sort.model';
import {CdkDragDrop, moveItemInArray} from '@angular/cdk/drag-drop';
import {TranslocoService} from '@jsverse/transloco';

export const SORT_OPTION_LABEL_KEYS: Readonly<Record<string, string>> = {
  title: 'book.sorting.options.title',
  fileName: 'book.sorting.options.fileName',
  filePath: 'book.sorting.options.filePath',
  author: 'book.sorting.options.author',
  authorSurnameVorname: 'book.sorting.options.authorSurnameVorname',
  seriesName: 'book.sorting.options.seriesName',
  seriesNumber: 'book.sorting.options.seriesNumber',
  lastReadTime: 'book.sorting.options.lastReadTime',
  personalRating: 'book.sorting.options.personalRating',
  addedOn: 'book.sorting.options.addedOn',
  fileSizeKb: 'book.sorting.options.fileSizeKb',
  locked: 'book.sorting.options.locked',
  publisher: 'book.sorting.options.publisher',
  publishedDate: 'book.sorting.options.publishedDate',
  readStatus: 'book.sorting.options.readStatus',
  dateFinished: 'book.sorting.options.dateFinished',
  readingProgress: 'book.sorting.options.readingProgress',
  bookType: 'book.sorting.options.bookType',
  amazonRating: 'book.sorting.options.amazonRating',
  amazonReviewCount: 'book.sorting.options.amazonReviewCount',
  goodreadsRating: 'book.sorting.options.goodreadsRating',
  goodreadsReviewCount: 'book.sorting.options.goodreadsReviewCount',
  hardcoverRating: 'book.sorting.options.hardcoverRating',
  hardcoverReviewCount: 'book.sorting.options.hardcoverReviewCount',
  ranobedbRating: 'book.sorting.options.ranobedbRating',
  narrator: 'book.sorting.options.narrator',
  pageCount: 'book.sorting.options.pageCount',
  random: 'book.sorting.options.random'
};

const SORT_FIELDS: { field: string; defaultLabel: string }[] = [
  {field: 'title', defaultLabel: 'Title'},
  {field: 'fileName', defaultLabel: 'File Name'},
  {field: 'filePath', defaultLabel: 'File Path'},
  {field: 'author', defaultLabel: 'Author'},
  {field: 'authorSurnameVorname', defaultLabel: 'Author (Surname)'},
  {field: 'seriesName', defaultLabel: 'Series Name'},
  {field: 'seriesNumber', defaultLabel: 'Series Number'},
  {field: 'lastReadTime', defaultLabel: 'Last Read'},
  {field: 'personalRating', defaultLabel: 'Personal Rating'},
  {field: 'addedOn', defaultLabel: 'Added On'},
  {field: 'fileSizeKb', defaultLabel: 'File Size'},
  {field: 'locked', defaultLabel: 'Locked'},
  {field: 'publisher', defaultLabel: 'Publisher'},
  {field: 'publishedDate', defaultLabel: 'Published Date'},
  {field: 'readStatus', defaultLabel: 'Read Status'},
  {field: 'dateFinished', defaultLabel: 'Date Finished'},
  {field: 'readingProgress', defaultLabel: 'Reading Progress'},
  {field: 'bookType', defaultLabel: 'Book Type'},
  {field: 'amazonRating', defaultLabel: 'Amazon Rating'},
  {field: 'amazonReviewCount', defaultLabel: 'Amazon #'},
  {field: 'goodreadsRating', defaultLabel: 'Goodreads Rating'},
  {field: 'goodreadsReviewCount', defaultLabel: 'Goodreads #'},
  {field: 'hardcoverRating', defaultLabel: 'Hardcover Rating'},
  {field: 'hardcoverReviewCount', defaultLabel: 'Hardcover #'},
  {field: 'ranobedbRating', defaultLabel: 'Ranobedb Rating'},
  {field: 'narrator', defaultLabel: 'Narrator'},
  {field: 'pageCount', defaultLabel: 'Pages'},
  {field: 'random', defaultLabel: 'Random'},
];

export class BookSorter {
  selectedSortCriteria: SortOption[] = [];

  sortOptions: SortOption[];

  constructor(private onSortChange: (criteria: SortOption[]) => void, translocoService?: TranslocoService) {
    this.sortOptions = SORT_FIELDS.map(({field, defaultLabel}) => {
      const key = SORT_OPTION_LABEL_KEYS[field];
      const label = key && translocoService ? translocoService.translate(key) : defaultLabel;
      return {label, field, direction: SortDirection.ASCENDING};
    });
  }

  // For backward compatibility - get first sort option
  get selectedSort(): SortOption | undefined {
    return this.selectedSortCriteria[0];
  }

  // For backward compatibility - set from single sort option
  set selectedSort(sort: SortOption | undefined) {
    if (sort) {
      this.selectedSortCriteria = [sort];
    } else {
      this.selectedSortCriteria = [];
    }
  }

  setSortCriteria(criteria: SortOption[]): void {
    this.selectedSortCriteria = [...criteria];
    this.updateSortOptions();
  }

  // Quick sort by field - toggles direction if already selected as primary sort
  sortBooks(field: string): void {
    const existingSort = this.sortOptions.find(opt => opt.field === field);
    if (!existingSort) return;

    // If this field is the first (primary) sort, toggle its direction
    if (this.selectedSortCriteria.length > 0 && this.selectedSortCriteria[0].field === field) {
      this.selectedSortCriteria[0] = {
        ...this.selectedSortCriteria[0],
        direction: this.selectedSortCriteria[0].direction === SortDirection.ASCENDING
          ? SortDirection.DESCENDING
          : SortDirection.ASCENDING
      };
    } else {
      // Set as the only sort criterion (single click behavior)
      this.selectedSortCriteria = [{
        label: existingSort.label,
        field: existingSort.field,
        direction: SortDirection.ASCENDING
      }];
    }

    this.updateSortOptions();
    this.onSortChange(this.selectedSortCriteria);
  }

  addSortCriterion(field: string): void {
    // Don't add if already exists
    if (this.selectedSortCriteria.some(c => c.field === field)) return;

    const option = this.sortOptions.find(opt => opt.field === field);
    if (!option) return;

    this.selectedSortCriteria.push({
      label: option.label,
      field: option.field,
      direction: SortDirection.ASCENDING
    });

    this.updateSortOptions();
    this.onSortChange(this.selectedSortCriteria);
  }

  removeSortCriterion(index: number): void {
    if (index < 0 || index >= this.selectedSortCriteria.length) return;

    this.selectedSortCriteria.splice(index, 1);
    this.updateSortOptions();
    this.onSortChange(this.selectedSortCriteria);
  }

  toggleCriterionDirection(index: number): void {
    if (index < 0 || index >= this.selectedSortCriteria.length) return;

    const criterion = this.selectedSortCriteria[index];
    this.selectedSortCriteria[index] = {
      ...criterion,
      direction: criterion.direction === SortDirection.ASCENDING
        ? SortDirection.DESCENDING
        : SortDirection.ASCENDING
    };

    this.updateSortOptions();
    this.onSortChange(this.selectedSortCriteria);
  }

  reorderCriteria(event: CdkDragDrop<SortOption[]>): void {
    moveItemInArray(this.selectedSortCriteria, event.previousIndex, event.currentIndex);
    this.updateSortOptions();
    this.onSortChange(this.selectedSortCriteria);
  }

  getAvailableSortOptions(): SortOption[] {
    const usedFields = new Set(this.selectedSortCriteria.map(c => c.field));
    return this.sortOptions.filter(opt => !usedFields.has(opt.field));
  }

  updateSortOptions(): void {
    const primaryField = this.selectedSortCriteria[0]?.field;
    const primaryDirection = this.selectedSortCriteria[0]?.direction;

    const directionIcon = primaryDirection === SortDirection.ASCENDING ? 'pi pi-arrow-up' : 'pi pi-arrow-down';

    this.sortOptions = this.sortOptions.map((option) => ({
      ...option,
      icon: option.field === primaryField ? directionIcon : '',
    }));
  }
}
