import {Component, EventEmitter, inject, Input, OnDestroy, OnInit, Output} from '@angular/core';
import {BehaviorSubject, combineLatest, Observable, of, Subject, takeUntil} from 'rxjs';
import {distinctUntilChanged, filter, map, take} from 'rxjs/operators';
import {BookService} from '../../../service/book.service';
import {Library} from '../../../model/library.model';
import {Shelf} from '../../../model/shelf.model';
import {EntityType} from '../book-browser.component';
import {Book, ReadStatus} from '../../../model/book.model';
import {Accordion, AccordionContent, AccordionHeader, AccordionPanel} from 'primeng/accordion';
import {AsyncPipe, NgClass, TitleCasePipe} from '@angular/common';
import {Badge} from 'primeng/badge';
import {FormsModule} from '@angular/forms';
import {SelectButton} from 'primeng/selectbutton';
import {UserService} from '../../../../settings/user-management/user.service';
import {FilterSortPreferenceService} from '../filters/filter-sorting-preferences.service';

type Filter<T> = { value: T; bookCount: number };

export const ratingRanges = [
  {id: '0to1', label: '0 to 1', min: 0, max: 1, sortIndex: 0},
  {id: '1to2', label: '1 to 2', min: 1, max: 2, sortIndex: 1},
  {id: '2to3', label: '2 to 3', min: 2, max: 3, sortIndex: 2},
  {id: '3to4', label: '3 to 4', min: 3, max: 4, sortIndex: 3},
  {id: '4to4.5', label: '4 to 4.5', min: 4, max: 4.5, sortIndex: 4},
  {id: '4.5plus', label: '4.5+', min: 4.5, max: Infinity, sortIndex: 5}
];

export const ratingRanges10 = [
  {id: '0to1', label: '0 to 1', min: 0, max: 1, sortIndex: 0},
  {id: '1to2', label: '1 to 2', min: 1, max: 2, sortIndex: 1},
  {id: '2to3', label: '2 to 3', min: 2, max: 3, sortIndex: 2},
  {id: '3to4', label: '3 to 4', min: 3, max: 4, sortIndex: 3},
  {id: '4to5', label: '4 to 5', min: 4, max: 5, sortIndex: 4},
  {id: '5to6', label: '5 to 6', min: 5, max: 6, sortIndex: 5},
  {id: '6to7', label: '6 to 7', min: 6, max: 7, sortIndex: 6},
  {id: '7to8', label: '7 to 8', min: 7, max: 8, sortIndex: 7},
  {id: '8to9', label: '8 to 9', min: 8, max: 9, sortIndex: 8},
  {id: '9to10', label: '9 to 10', min: 9, max: 10, sortIndex: 9}
];

export const fileSizeRanges = [
  {id: '<1mb', label: '< 1 MB', min: 0, max: 1024, sortIndex: 0},
  {id: '1to10mb', label: '1–10 MB', min: 1024, max: 10240, sortIndex: 1},
  {id: '10to50mb', label: '10–50 MB', min: 10240, max: 51200, sortIndex: 2},
  {id: '50to100mb', label: '50–100 MB', min: 51200, max: 102400, sortIndex: 3},
  {id: '250to500mb', label: '250–500 MB', min: 256000, max: 512000, sortIndex: 4},
  {id: '500mbto1gb', label: '0.5–1 GB', min: 512000, max: 1048576, sortIndex: 5},
  {id: '1to2gb', label: '1–2 GB', min: 1048576, max: 2097152, sortIndex: 6},
  {id: '5plusgb', label: '5+ GB', min: 5242880, max: Infinity, sortIndex: 7}
];

export const pageCountRanges = [
  {id: '<50', label: '< 50 pages', min: 0, max: 50, sortIndex: 0},
  {id: '50to100', label: '50–100 pages', min: 50, max: 100, sortIndex: 1},
  {id: '100to200', label: '100–200 pages', min: 100, max: 200, sortIndex: 2},
  {id: '200to400', label: '200–400 pages', min: 200, max: 400, sortIndex: 3},
  {id: '400to600', label: '400–600 pages', min: 400, max: 600, sortIndex: 4},
  {id: '600to1000', label: '600–1000 pages', min: 600, max: 1000, sortIndex: 5},
  {id: '1000plus', label: '1000+ pages', min: 1000, max: Infinity, sortIndex: 6}
];

export const matchScoreRanges = [
  {id: '0.95-1.0', min: 0.95, max: 1.01, label: 'Outstanding (95–100%)', sortIndex: 0},
  {id: '0.90-0.94', min: 0.90, max: 0.95, label: 'Excellent (90–94%)', sortIndex: 1},
  {id: '0.80-0.89', min: 0.80, max: 0.90, label: 'Great (80–89%)', sortIndex: 2},
  {id: '0.70-0.79', min: 0.70, max: 0.80, label: 'Good (70–79%)', sortIndex: 3},
  {id: '0.50-0.69', min: 0.50, max: 0.70, label: 'Fair (50–69%)', sortIndex: 4},
  {id: '0.30-0.49', min: 0.30, max: 0.50, label: 'Weak (30–49%)', sortIndex: 5},
  {id: '0.00-0.29', min: 0.00, max: 0.30, label: 'Poor (0–29%)', sortIndex: 6}
];

function getLanguageFilter(book: Book): { id: string; name: string }[] {
  const lang = book.metadata?.language;
  return lang ? [{id: lang, name: lang}] : [];
}

function getFileSizeRangeFilters(sizeKb?: number): { id: string; name: string; sortIndex?: number }[] {
  if (sizeKb == null) return [];
  const match = fileSizeRanges.find(r => sizeKb >= r.min && sizeKb < r.max);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
}

function getRatingRangeFilters(rating?: number): { id: string; name: string; sortIndex?: number }[] {
  if (rating == null) return [];
  const match = ratingRanges.find(r => rating >= r.min && rating < r.max);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
}

function getRatingRangeFilters10(rating?: number): { id: string; name: string; sortIndex?: number }[] {
  if (rating == null) return [];
  const match = ratingRanges10.find(r => rating >= r.min && rating < r.max);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
}

function extractPublishedYearFilter(book: Book): { id: number; name: string }[] {
  const date = book.metadata?.publishedDate;
  if (!date) return [];
  const year = new Date(date).getFullYear();
  return [{id: year, name: year.toString()}];
}

function getShelfStatusFilter(book: Book): { id: string; name: string }[] {
  const isShelved = book.shelves?.length! > 0;
  return [{id: isShelved ? 'shelved' : 'unshelved', name: isShelved ? 'Shelved' : 'Unshelved'}];
}

function getPageCountRangeFilters(pageCount?: number): { id: string; name: string; sortIndex?: number }[] {
  if (pageCount == null) return [];
  const match = pageCountRanges.find(r => pageCount >= r.min && pageCount < r.max);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
}

function getMatchScoreRangeFilters(score?: number | null): { id: string; name: string; sortIndex?: number }[] {
  if (score == null) return [];
  const match = matchScoreRanges.find(r => score >= r.min && score < r.max);
  return match ? [{id: match.id, name: match.label, sortIndex: match.sortIndex}] : [];
}

const readStatusLabels: Record<ReadStatus, string> = {
  [ReadStatus.UNREAD]: 'Unread',
  [ReadStatus.READING]: 'Reading',
  [ReadStatus.RE_READING]: 'Re-reading',
  [ReadStatus.PARTIALLY_READ]: 'Partially Read',
  [ReadStatus.PAUSED]: 'Paused',
  [ReadStatus.READ]: 'Read',
  [ReadStatus.WONT_READ]: 'Won’t Read',
  [ReadStatus.ABANDONED]: 'Abandoned',
};

function getReadStatusName(status?: ReadStatus | null): string {
  return status != null ? readStatusLabels[status] ?? 'Unknown' : 'Unknown';
}

@Component({
  selector: 'app-book-filter',
  templateUrl: './book-filter.component.html',
  styleUrls: ['./book-filter.component.scss'],
  standalone: true,
  imports: [
    Accordion,
    AccordionPanel,
    AccordionHeader,
    AccordionContent,
    NgClass,
    Badge,
    AsyncPipe,
    TitleCasePipe,
    FormsModule,
    SelectButton
  ]
})
export class BookFilterComponent implements OnInit, OnDestroy {
  @Output() filterSelected = new EventEmitter<Record<string, any> | null>();
  @Output() filterModeChanged = new EventEmitter<'and' | 'or'>();

  @Input() showFilters: boolean = true;
  @Input() entity$!: Observable<Library | Shelf | null> | undefined;
  @Input() entityType$!: Observable<EntityType> | undefined;
  @Input() resetFilter$!: Subject<void>;

  activeFilters: Record<string, any> = {};
  filterStreams: Record<string, Observable<Filter<any>[]>> = {};
  filterTypes: string[] = [];
  filterModeOptions = [
    {label: 'AND', value: 'and'},
    {label: 'OR', value: 'or'}
  ];
  private _selectedFilterMode: 'and' | 'or' = 'and';
  expandedPanels: number = 0;
  readonly filterLabels: Record<string, string> = {
    author: 'Author',
    category: 'Category',
    series: 'Series',
    publisher: 'Publisher',
    readStatus: 'Read Status',
    personalRating: 'Personal Rating',
    matchScore: 'Metadata Match Score',
    amazonRating: 'Amazon Rating',
    goodreadsRating: 'Goodreads Rating',
    hardcoverRating: 'Hardcover Rating',
    publishedDate: 'Published Year',
    fileSize: 'File Size',
    shelfStatus: 'Shelf Status',
    pageCount: 'Page Count',
    language: 'Language'
  };

  private destroy$ = new Subject<void>();

  bookService = inject(BookService);
  userService = inject(UserService);
  filterSortPreferenceService = inject(FilterSortPreferenceService);

  ngOnInit(): void {
    combineLatest([
      this.filterSortPreferenceService.sortMode$.pipe(distinctUntilChanged()),
      this.entity$ ?? of(null),
      this.entityType$ ?? of(EntityType.ALL_BOOKS)
    ])
      .pipe(takeUntil(this.destroy$))
      .subscribe(([sortMode]) => {
        this.filterStreams = {
          author: this.getFilterStream((book: Book) => book.metadata?.authors.map(name => ({id: name, name})) || [], 'id', 'name', sortMode),
          category: this.getFilterStream((book: Book) => book.metadata?.categories.map(name => ({id: name, name})) || [], 'id', 'name', sortMode),
          series: this.getFilterStream((book) => (book.metadata?.seriesName ? [{id: book.metadata.seriesName, name: book.metadata.seriesName}] : []), 'id', 'name', sortMode),
          publisher: this.getFilterStream((book) => (book.metadata?.publisher ? [{id: book.metadata.publisher, name: book.metadata.publisher}] : []), 'id', 'name', sortMode),
          readStatus: this.getFilterStream((book: Book) => [{id: book.readStatus ?? ReadStatus.UNREAD, name: getReadStatusName(book.readStatus)}], 'id', 'name', sortMode),
          matchScore: this.getFilterStream((book: Book) => getMatchScoreRangeFilters(book.metadataMatchScore), 'id', 'name', 'sortIndex'),
          personalRating: this.getFilterStream((book: Book) => getRatingRangeFilters10(book.metadata?.personalRating!), 'id', 'name', 'sortIndex'),
          amazonRating: this.getFilterStream((book: Book) => getRatingRangeFilters(book.metadata?.amazonRating!), 'id', 'name', 'sortIndex'),
          goodreadsRating: this.getFilterStream((book: Book) => getRatingRangeFilters(book.metadata?.goodreadsRating!), 'id', 'name', 'sortIndex'),
          hardcoverRating: this.getFilterStream((book: Book) => getRatingRangeFilters(book.metadata?.hardcoverRating!), 'id', 'name', 'sortIndex'),
          shelfStatus: this.getFilterStream(getShelfStatusFilter, 'id', 'name', sortMode),
          publishedDate: this.getFilterStream(extractPublishedYearFilter, 'id', 'name', sortMode),
          language: this.getFilterStream(getLanguageFilter, 'id', 'name', sortMode),
          fileSize: this.getFilterStream((book: Book) => getFileSizeRangeFilters(book.fileSizeKb), 'id', 'name', 'sortIndex'),
          pageCount: this.getFilterStream((book: Book) => getPageCountRangeFilters(book.metadata?.pageCount!), 'id', 'name', 'sortIndex'),
        };

        this.filterTypes = Object.keys(this.filterStreams);
        this.setExpandedPanels();
      });

    if (this.resetFilter$) {
      this.resetFilter$.pipe(takeUntil(this.destroy$)).subscribe(() => this.clearActiveFilter());
    }
  }

  private getFilterStream<T>(
    extractor: (book: Book) => T[] | undefined,
    idKey: keyof T,
    nameKey: keyof T,
    sortMode: 'count' | 'alphabetical' | 'sortIndex' = 'count'
  ): Observable<Filter<T[keyof T]>[]> {
    return combineLatest([
      this.bookService.bookState$,
      this.entity$ ?? of(null),
      this.entityType$ ?? of(EntityType.ALL_BOOKS)
    ]).pipe(
      map(([state, entity, entityType]) => {
        const filteredBooks = this.filterBooksByEntityType(state.books || [], entity, entityType);
        const filterMap = new Map<any, Filter<any>>();

        filteredBooks.forEach((book) => {
          (extractor(book) || []).forEach((item) => {
            const id = item[idKey];
            if (!filterMap.has(id)) {
              filterMap.set(id, {value: item, bookCount: 0});
            }
            filterMap.get(id)!.bookCount += 1;
          });
        });

        const result = Array.from(filterMap.values());

        return result.sort((a, b) => {
          if (sortMode === 'sortIndex') {
            return (a.value.sortIndex ?? 999) - (b.value.sortIndex ?? 999);
          }
          if (sortMode === 'alphabetical') {
            return a.value[nameKey].toString().localeCompare(b.value[nameKey].toString());
          }
          return (
            b.bookCount - a.bookCount ||
            a.value[nameKey].toString().localeCompare(b.value[nameKey].toString())
          );
        });
      })
    );
  }

  get selectedFilterMode(): 'and' | 'or' {
    return this._selectedFilterMode;
  }

  set selectedFilterMode(mode: 'and' | 'or') {
    this._selectedFilterMode = mode;
    this.filterModeChanged.emit(mode);
  }

  private filterBooksByEntityType(books: Book[], entity: any, entityType: EntityType): Book[] {
    if (entityType === EntityType.LIBRARY && entity && 'id' in entity) {
      return books.filter((book) => book.libraryId === entity.id);
    }
    if (entityType === EntityType.SHELF && entity && 'id' in entity) {
      return books.filter((book) => book.shelves?.some((shelf) => shelf.id === entity.id));
    }
    return books;
  }

  handleFilterClick(filterType: string, value: any): void {
    if (!this.activeFilters[filterType]) {
      this.activeFilters[filterType] = [];
    }

    const index = this.activeFilters[filterType].indexOf(value);
    if (index > -1) {
      this.activeFilters[filterType].splice(index, 1);
      if (this.activeFilters[filterType].length === 0) {
        delete this.activeFilters[filterType];
      }
    } else {
      this.activeFilters[filterType].push(value);
    }

    if (Object.keys(this.activeFilters).length === 0) {
      this.filterSelected.emit(null);
    } else {
      this.filterSelected.emit({...this.activeFilters});
    }
  }

  setFilters(filters: Record<string, any>) {
    this.activeFilters = {};

    for (const [key, value] of Object.entries(filters)) {
      if (Array.isArray(value)) {
        this.activeFilters[key] = [...value];
      } else {
        this.activeFilters[key] = [value];
      }
    }
    this.filterSelected.emit({...this.activeFilters});
  }

  clearActiveFilter() {
    this.activeFilters = {};
    this.filterSelected.emit(null);
  }

  setExpandedPanels(): void {
    const firstActiveIndex = this.filterTypes.findIndex(
      type => this.activeFilters[type]?.length
    );
    this.expandedPanels = firstActiveIndex !== -1 ? firstActiveIndex : 0;
  }

  onFiltersChanged(): void {
    this.setExpandedPanels();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
