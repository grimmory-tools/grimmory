import {SortDirection, SortOption} from '../../../model/sort.model';
import {CdkDragDrop, moveItemInArray} from '@angular/cdk/drag-drop';

export class BookSorter {
  selectedSortCriteria: SortOption[] = [];

  sortOptions: SortOption[] = [
    {label: 'Title', field: 'title', direction: SortDirection.ASCENDING},
    {label: 'Title + Series', field: 'titleSeries', direction: SortDirection.ASCENDING},
    {label: 'File Name', field: 'fileName', direction: SortDirection.ASCENDING},
    {label: 'File Path', field: 'filePath', direction: SortDirection.ASCENDING},
    {label: 'Author', field: 'author', direction: SortDirection.ASCENDING},
    {label: 'Author (Surname)', field: 'authorSurnameVorname', direction: SortDirection.ASCENDING},
    {label: 'Author + Series', field: 'authorSeries', direction: SortDirection.ASCENDING},
    {label: 'Last Read', field: 'lastReadTime', direction: SortDirection.ASCENDING},
    {label: 'Personal Rating', field: 'personalRating', direction: SortDirection.ASCENDING},
    {label: 'Added On', field: 'addedOn', direction: SortDirection.ASCENDING},
    {label: 'File Size', field: 'fileSizeKb', direction: SortDirection.ASCENDING},
    {label: 'Locked', field: 'locked', direction: SortDirection.ASCENDING},
    {label: 'Publisher', field: 'publisher', direction: SortDirection.ASCENDING},
    {label: 'Published Date', field: 'publishedDate', direction: SortDirection.ASCENDING},
    {label: 'Amazon Rating', field: 'amazonRating', direction: SortDirection.ASCENDING},
    {label: 'Amazon #', field: 'amazonReviewCount', direction: SortDirection.ASCENDING},
    {label: 'Goodreads Rating', field: 'goodreadsRating', direction: SortDirection.ASCENDING},
    {label: 'Goodreads #', field: 'goodreadsReviewCount', direction: SortDirection.ASCENDING},
    {label: 'Hardcover Rating', field: 'hardcoverRating', direction: SortDirection.ASCENDING},
    {label: 'Hardcover #', field: 'hardcoverReviewCount', direction: SortDirection.ASCENDING},
    {label: 'Ranobedb Rating', field: 'ranobedbRating', direction: SortDirection.ASCENDING},
    {label: 'Pages', field: 'pageCount', direction: SortDirection.ASCENDING},
    {label: 'Random', field: 'random', direction: SortDirection.ASCENDING},
  ];

  constructor(private onSortChange: (criteria: SortOption[]) => void) {
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
