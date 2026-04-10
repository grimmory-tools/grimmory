import {computed, inject, Injectable, Signal} from '@angular/core';
import {Book} from '../../../model/book.model';
import {Library} from '../../../model/library.model';
import {Shelf} from '../../../model/shelf.model';
import {MagicShelf} from '../../../../magic-shelf/service/magic-shelf.service';
import {BookService} from '../../../service/book.service';
import {AppBooksApiService} from '../../../service/app-books-api.service';
import {LibraryService} from '../../../service/library.service';
import {BookRuleEvaluatorService} from '../../../../magic-shelf/service/book-rule-evaluator.service';
import {GroupRule} from '../../../../magic-shelf/component/magic-shelf-component';
import {EntityType} from '../book-browser.component';
import {Filter, FILTER_CONFIGS, FILTER_EXTRACTORS, FilterType, FilterValue, NUMERIC_ID_FILTER_TYPES, SortMode} from './book-filter.config';
import {filterBooksByFilters} from '../filters/sidebar-filter';
import {BookFilterMode} from '../../../../settings/user-management/user.service';
import {AppFilterOptions, CountedOption, LanguageOption} from '../../../model/app-book.model';

const MAX_FILTER_ITEMS = 100;

@Injectable({providedIn: 'root'})
export class BookFilterService {
  private readonly bookService = inject(BookService);
  private readonly appBooksApi = inject(AppBooksApiService);
  private readonly libraryService = inject(LibraryService);
  private readonly bookRuleEvaluatorService = inject(BookRuleEvaluatorService);

  /** Server-supported filter types -- these use /api/v1/app/filter-options data. */
  private static readonly SERVER_FILTER_TYPES = new Set<FilterType>([
    'author', 'category', 'series', 'publisher', 'tag', 'mood',
    'narrator', 'language', 'readStatus', 'bookType',
  ]);

  createFilterSignals(
    entity: Signal<Library | Shelf | MagicShelf | null>,
    entityType: Signal<EntityType>,
    activeFilters: Signal<Record<string, unknown[]> | null>,
    filterMode: Signal<BookFilterMode>
  ): Record<FilterType, Signal<Filter[]>> {
    const signals = {} as Record<FilterType, Signal<Filter[]>>;

    // Server-backed filter types use the filter-options API
    for (const type of BookFilterService.SERVER_FILTER_TYPES) {
      signals[type] = computed(() => {
        const options = this.appBooksApi.filterOptions();
        if (!options) return [];
        return this.serverOptionsToFilters(type, options);
      });
    }

    // Client-side filter types that don't have server support use loaded books
    const filteredBooks = computed(() =>
      this.appBooksApi.books()
    );

    for (const [type, config] of Object.entries(FILTER_CONFIGS)) {
      const filterType = type as Exclude<FilterType, 'library'>;
      if (BookFilterService.SERVER_FILTER_TYPES.has(filterType)) continue;
      signals[filterType] = computed(() => {
        const books = filterBooksByFilters(filteredBooks(), activeFilters(), filterMode(), filterType);
        return this.buildAndSortFilters(books, FILTER_EXTRACTORS[filterType], config.sortMode);
      });
    }

    if (!signals.library) {
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
    }

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

  private serverOptionsToFilters(type: FilterType, options: AppFilterOptions): Filter[] {
    switch (type) {
      case 'author':
        return this.countedToFilters(options.authors);
      case 'category':
        return this.countedToFilters(options.categories);
      case 'series':
        return this.countedToFilters(options.series);
      case 'publisher':
        return this.countedToFilters(options.publishers);
      case 'tag':
        return this.countedToFilters(options.tags);
      case 'mood':
        return this.countedToFilters(options.moods);
      case 'narrator':
        return this.countedToFilters(options.narrators);
      case 'language':
        return (options.languages ?? []).map((lang: LanguageOption) => ({
          value: {id: lang.code, name: lang.label || lang.code},
          bookCount: lang.count,
        }));
      case 'readStatus':
        return this.countedToFilters(options.readStatuses);
      case 'bookType':
        return this.countedToFilters(options.fileTypes);
      default:
        return [];
    }
  }

  private countedToFilters(items: CountedOption[]): Filter[] {
    return (items ?? []).map(item => ({
      value: {id: item.name, name: item.name},
      bookCount: item.count,
    }));
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
