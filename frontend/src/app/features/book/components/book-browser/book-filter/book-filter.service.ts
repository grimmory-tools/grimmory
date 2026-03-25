import {computed, inject, Injectable, Signal} from '@angular/core';
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

  createFilterSignals(
    entity: Signal<Library | Shelf | MagicShelf | null>,
    entityType: Signal<EntityType>,
    activeFilters: Signal<Record<string, unknown[]> | null>,
    filterMode: Signal<BookFilterMode>
  ): Record<FilterType, Signal<Filter[]>> {
    const filteredBooks = computed(() =>
      this.filterBooksByEntity(this.bookService.books(), entity(), entityType())
    );

    const signals = {} as Record<FilterType, Signal<Filter[]>>;

    for (const [type, config] of Object.entries(FILTER_CONFIGS)) {
      const filterType = type as Exclude<FilterType, 'library'>;
      signals[filterType] = computed(() => {
        const books = filterBooksByFilters(filteredBooks(), activeFilters(), filterMode(), filterType);
        return this.buildAndSortFilters(books, FILTER_EXTRACTORS[filterType], config.sortMode);
      });
    }

    signals.library = computed(() => {
      const books = filterBooksByFilters(filteredBooks(), activeFilters(), filterMode(), 'library');
      const libraries = this.libraryService.libraries();

      const libraryMap = new Map(
        libraries
          .filter(lib => lib.id !== undefined)
          .map(lib => [lib.id!, lib.name])
      );

      const filterMap = new Map<number, Filter>();

      for (const book of books) {
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
    });

    return signals;
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

      case EntityType.SHELF: {
        const shelfId = (entity as Shelf).id;
        return books.filter(book => book.shelves?.some(s => s.id === shelfId));
      }

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
