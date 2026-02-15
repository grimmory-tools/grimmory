import {inject, Injectable} from '@angular/core';
import {combineLatest, map, Observable, of, shareReplay} from 'rxjs';
import {Book} from '../../../model/book.model';
import {Library} from '../../../model/library.model';
import {Shelf} from '../../../model/shelf.model';
import {MagicShelf} from '../../../../magic-shelf/service/magic-shelf.service';
import {BookService} from '../../../service/book.service';
import {LibraryService} from '../../../service/library.service';
import {BookRuleEvaluatorService} from '../../../../magic-shelf/service/book-rule-evaluator.service';
import {GroupRule} from '../../../../magic-shelf/component/magic-shelf-component';
import {EntityType} from '../book-browser.component';
import {Filter, FILTER_CONFIGS, FILTER_EXTRACTORS, FilterType, FilterValue, NUMERIC_ID_FILTER_TYPES, SortMode} from './book-filter.config';
import {filterBooksByFilters} from '../filters/sidebar-filter';
import {BookFilterMode} from '../../../../settings/user-management/user.service';

const MAX_FILTER_ITEMS = 100;

@Injectable({providedIn: 'root'})
export class BookFilterService {
  private readonly bookService = inject(BookService);
  private readonly libraryService = inject(LibraryService);
  private readonly bookRuleEvaluatorService = inject(BookRuleEvaluatorService);

  createFilterStreams(
    entity$: Observable<Library | Shelf | MagicShelf | null>,
    entityType$: Observable<EntityType>,
    activeFilters$: Observable<Record<string, unknown[]> | null> = of(null),
    filterMode$: Observable<BookFilterMode> = of('and')
  ): Record<FilterType, Observable<Filter[]>> {
    const filteredBooks$ = this.createFilteredBooksStream(entity$, entityType$);

    const streams = {} as Record<FilterType, Observable<Filter[]>>;

    for (const [type, config] of Object.entries(FILTER_CONFIGS)) {
      const filterType = type as Exclude<FilterType, 'library'>;
      streams[filterType] = this.createCascadingFilterStream(
        filteredBooks$,
        activeFilters$,
        filterMode$,
        filterType,
        FILTER_EXTRACTORS[filterType],
        config.sortMode
      );
    }

    streams.library = this.createCascadingLibraryFilterStream(filteredBooks$, activeFilters$, filterMode$);

    return streams;
  }

  filterBooksByEntity(
    books: Book[],
    entity: Library | Shelf | MagicShelf | null,
    entityType: EntityType
  ): Book[] {
    if (!entity) return books;

    switch (entityType) {
      case EntityType.LIBRARY:
        return books.filter(book => book.libraryId === (entity as Library).id);

      case EntityType.SHELF:
        const shelfId = (entity as Shelf).id;
        return books.filter(book => book.shelves?.some(s => s.id === shelfId));

      case EntityType.MAGIC_SHELF:
        return this.filterByMagicShelf(books, entity as MagicShelf);

      default:
        return books;
    }
  }

  processFilterValue(key: string, value: unknown): unknown {
    if (NUMERIC_ID_FILTER_TYPES.has(key as FilterType) && typeof value === 'string') {
      return Number(value);
    }
    return value;
  }

  isNumericFilter(filterType: string): boolean {
    return NUMERIC_ID_FILTER_TYPES.has(filterType as FilterType);
  }

  private createFilteredBooksStream(
    entity$: Observable<Library | Shelf | MagicShelf | null>,
    entityType$: Observable<EntityType>
  ): Observable<Book[]> {
    return combineLatest([
      this.bookService.bookState$,
      entity$,
      entityType$
    ]).pipe(
      map(([state, entity, entityType]) =>
        this.filterBooksByEntity(state.books || [], entity, entityType)
      ),
      shareReplay({bufferSize: 1, refCount: true})
    );
  }

  private createCascadingFilterStream(
    books$: Observable<Book[]>,
    activeFilters$: Observable<Record<string, unknown[]> | null>,
    filterMode$: Observable<BookFilterMode>,
    filterType: FilterType,
    extractor: (book: Book) => FilterValue[],
    sortMode: SortMode
  ): Observable<Filter[]> {
    return combineLatest([books$, activeFilters$, filterMode$]).pipe(
      map(([books, activeFilters, mode]) => {
        const filteredBooks = filterBooksByFilters(books, activeFilters, mode, filterType);
        return this.buildAndSortFilters(filteredBooks, extractor, sortMode);
      }),
      shareReplay({bufferSize: 1, refCount: true})
    );
  }

  private createCascadingLibraryFilterStream(
    books$: Observable<Book[]>,
    activeFilters$: Observable<Record<string, unknown[]> | null>,
    filterMode$: Observable<BookFilterMode>
  ): Observable<Filter[]> {
    return combineLatest([books$, this.libraryService.libraryState$, activeFilters$, filterMode$]).pipe(
      map(([books, libraryState, activeFilters, mode]) => {
        const filteredBooks = filterBooksByFilters(books, activeFilters, mode, 'library');

        const libraryMap = new Map(
          (libraryState.libraries || [])
            .filter(lib => lib.id !== undefined)
            .map(lib => [lib.id!, lib.name])
        );

        const filterMap = new Map<number, Filter>();

        for (const book of filteredBooks) {
          if (book.libraryId == null) continue;

          if (!filterMap.has(book.libraryId)) {
            filterMap.set(book.libraryId, {
              value: {
                id: book.libraryId,
                name: libraryMap.get(book.libraryId) || `Library ${book.libraryId}`
              },
              bookCount: 0
            });
          }
          filterMap.get(book.libraryId)!.bookCount++;
        }

        return this.sortFiltersByCount(Array.from(filterMap.values()));
      }),
      shareReplay({bufferSize: 1, refCount: true})
    );
  }

  private buildAndSortFilters(
    books: Book[],
    extractor: (book: Book) => FilterValue[],
    sortMode: SortMode
  ): Filter[] {
    const filterMap = new Map<unknown, Filter>();

    for (const book of books) {
      for (const item of extractor(book)) {
        const id = item.id;
        if (!filterMap.has(id)) {
          filterMap.set(id, {value: item, bookCount: 0});
        }
        filterMap.get(id)!.bookCount++;
      }
    }

    const filters = Array.from(filterMap.values());
    const sorted = sortMode === 'sortIndex'
      ? this.sortFiltersBySortIndex(filters)
      : this.sortFiltersByCount(filters);

    return sorted.slice(0, MAX_FILTER_ITEMS);
  }

  private sortFiltersByCount(filters: Filter[]): Filter[] {
    return filters.sort((a, b) => {
      if (b.bookCount !== a.bookCount) return b.bookCount - a.bookCount;
      return this.compareNames(a, b);
    });
  }

  private sortFiltersBySortIndex(filters: Filter[]): Filter[] {
    return filters.sort((a, b) => {
      const aIndex = (a.value as { sortIndex?: number }).sortIndex ?? 999;
      const bIndex = (b.value as { sortIndex?: number }).sortIndex ?? 999;
      if (aIndex !== bIndex) return aIndex - bIndex;
      return this.compareNames(a, b);
    });
  }

  private compareNames(a: Filter, b: Filter): number {
    const aName = String((a.value as { name?: string }).name ?? '');
    const bName = String((b.value as { name?: string }).name ?? '');
    return aName.localeCompare(bName);
  }

  private filterByMagicShelf(books: Book[], magicShelf: MagicShelf): Book[] {
    if (!magicShelf.filterJson) return [];
    try {
      const groupRule = JSON.parse(magicShelf.filterJson) as GroupRule;
      return books.filter(book => this.bookRuleEvaluatorService.evaluateGroup(book, groupRule, books));
    } catch {
      console.warn('Invalid filterJson for MagicShelf');
      return [];
    }
  }
}
