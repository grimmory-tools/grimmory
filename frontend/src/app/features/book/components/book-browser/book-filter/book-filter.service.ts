import {computed, inject, Injectable, Signal} from '@angular/core';
import {Book} from '../../../model/book.model';
import {Library} from '../../../model/library.model';
import {Shelf} from '../../../model/shelf.model';
import {MagicShelf} from '../../../../magic-shelf/service/magic-shelf.service';
import {AppBooksApiService} from '../../../service/app-books-api.service';
import {BookRuleEvaluatorService} from '../../../../magic-shelf/service/book-rule-evaluator.service';
import {GroupRule} from '../../../../magic-shelf/component/magic-shelf-component';
import {EntityType} from '../book-browser.component';
import {
  ageRatingRanges,
  CONTENT_RATING_LABELS,
  Filter,
  fileSizeRanges,
  FilterType,
  matchScoreRanges,
  NUMERIC_ID_FILTER_TYPES,
  pageCountRanges,
  ratingRanges,
  READ_STATUS_LABELS,
} from './book-filter.config';
import {AppFilterOptions, CountedOption, LanguageOption} from '../../../model/app-book.model';
import {ReadStatus} from '../../../model/book.model';

@Injectable({providedIn: 'root'})
export class BookFilterService {
  private readonly appBooksApi = inject(AppBooksApiService);
  private readonly bookRuleEvaluatorService = inject(BookRuleEvaluatorService);

  /** Cached filter signals — created once, shared across component instances. */
  private _filterSignals: Record<FilterType, Signal<Filter[]>> | null = null;

  get filterSignals(): Record<FilterType, Signal<Filter[]>> {
    if (!this._filterSignals) {
      this._filterSignals = this.buildFilterSignals();
    }
    return this._filterSignals;
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

  private buildFilterSignals(): Record<FilterType, Signal<Filter[]>> {
    const signals = {} as Record<FilterType, Signal<Filter[]>>;

    signals['author'] = computed(() => this.countedToFilters(this.appBooksApi.authorOptions()));
    signals['category'] = computed(() => this.countedToFilters(this.appBooksApi.categoryOptions()));
    signals['series'] = computed(() => this.countedToFilters(this.appBooksApi.seriesOptions()));
    signals['publisher'] = computed(() => this.countedToFilters(this.appBooksApi.publisherOptions()));
    signals['tag'] = computed(() => this.countedToFilters(this.appBooksApi.tagOptions()));
    signals['mood'] = computed(() => this.countedToFilters(this.appBooksApi.moodOptions()));
    signals['narrator'] = computed(() => this.countedToFilters(this.appBooksApi.narratorOptions()));
    signals['language'] = computed(() => this.appBooksApi.languageOptions().map((lang: LanguageOption) => ({
      value: {id: lang.code, name: lang.label || lang.code},
      bookCount: lang.count,
    })));
    signals['readStatus'] = computed(() => this.appBooksApi.readStatusOptions().map(item => ({
      value: {id: item.name, name: READ_STATUS_LABELS[item.name as ReadStatus] || item.name},
      bookCount: item.count
    })));
    signals['bookType'] = computed(() => this.countedToFilters(this.appBooksApi.fileTypeOptions()));
    signals['ageRating'] = computed(() => this.rangedFilters(this.appBooksApi.ageRatingOptions(), ageRatingRanges));
    signals['contentRating'] = computed(() => this.appBooksApi.contentRatingOptions().map(item => ({
      value: {id: item.name, name: CONTENT_RATING_LABELS[item.name] || item.name},
      bookCount: item.count
    })));
    signals['matchScore'] = computed(() => this.rangedFilters(this.appBooksApi.matchScoreOptions(), matchScoreRanges));
    signals['publishedDate'] = computed(() => this.appBooksApi.publishedYearOptions().map(item => ({
      value: {id: item.name, name: item.name},
      bookCount: item.count
    })));
    signals['fileSize'] = computed(() => this.rangedFilters(this.appBooksApi.fileSizeOptions(), fileSizeRanges));
    signals['personalRating'] = computed(() => this.appBooksApi.personalRatingOptions().map(item => ({
      value: {id: Number(item.name), name: item.name},
      bookCount: item.count
    })));
    signals['amazonRating'] = computed(() => this.rangedFilters(this.appBooksApi.amazonRatingOptions(), ratingRanges));
    signals['goodreadsRating'] = computed(() => this.rangedFilters(this.appBooksApi.goodreadsRatingOptions(), ratingRanges));
    signals['hardcoverRating'] = computed(() => this.rangedFilters(this.appBooksApi.hardcoverRatingOptions(), ratingRanges));
    signals['pageCount'] = computed(() => this.rangedFilters(this.appBooksApi.pageCountOptions(), pageCountRanges));
    signals['shelfStatus'] = computed(() => this.appBooksApi.shelfStatusOptions().map(item => ({
      value: {id: item.name, name: item.name.charAt(0).toUpperCase() + item.name.slice(1)},
      bookCount: item.count
    })));
    signals['comicCharacter'] = computed(() => this.countedToFilters(this.appBooksApi.comicCharacterOptions()));
    signals['comicTeam'] = computed(() => this.countedToFilters(this.appBooksApi.comicTeamOptions()));
    signals['comicLocation'] = computed(() => this.countedToFilters(this.appBooksApi.comicLocationOptions()));
    signals['comicCreator'] = computed(() => this.appBooksApi.comicCreatorOptions().map(item => {
      const parts = item.name.split(':');
      const role = parts.pop()!;
      const name = parts.join(':');
      return {
        value: {id: item.name, name: `${name} (${role})`},
        bookCount: item.count
      };
    }));
    signals['shelf'] = computed(() => this.appBooksApi.shelfOptions().map(item => {
      const [id, ...nameParts] = item.name.split(':');
      return {
        value: {id: Number(id), name: nameParts.join(':')},
        bookCount: item.count
      };
    }));
    signals['library'] = computed(() => this.appBooksApi.libraryOptions().map(item => {
      const [id, ...nameParts] = item.name.split(':');
      return {
        value: {id: Number(id), name: nameParts.join(':')},
        bookCount: item.count
      };
    }));

    return signals;
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
        return (options.readStatuses ?? []).map(item => ({
          value: {id: item.name, name: READ_STATUS_LABELS[item.name as ReadStatus] || item.name},
          bookCount: item.count
        }));
      case 'bookType':
        return this.countedToFilters(options.fileTypes);
      case 'ageRating':
        return this.rangedFilters(options.ageRatings, ageRatingRanges);
      case 'contentRating':
        return (options.contentRatings ?? []).map(item => ({
          value: {id: item.name, name: CONTENT_RATING_LABELS[item.name] || item.name},
          bookCount: item.count
        }));
      case 'matchScore':
        return this.rangedFilters(options.matchScores, matchScoreRanges);
      case 'publishedDate':
        return (options.publishedYears ?? []).map(item => ({
          value: {id: item.name, name: item.name},
          bookCount: item.count
        }));
      case 'fileSize':
        return this.rangedFilters(options.fileSizes, fileSizeRanges);
      case 'personalRating':
        return (options.personalRatings ?? []).map(item => ({
          value: {id: Number(item.name), name: item.name},
          bookCount: item.count
        }));
      case 'amazonRating':
        return this.rangedFilters(options.amazonRatings, ratingRanges);
      case 'goodreadsRating':
        return this.rangedFilters(options.goodreadsRatings, ratingRanges);
      case 'hardcoverRating':
        return this.rangedFilters(options.hardcoverRatings, ratingRanges);
      case 'pageCount':
        return this.rangedFilters(options.pageCounts, pageCountRanges);
      case 'shelfStatus':
        return (options.shelfStatuses ?? []).map(item => ({
          value: {id: item.name, name: item.name.charAt(0).toUpperCase() + item.name.slice(1)},
          bookCount: item.count
        }));
      case 'comicCharacter':
        return this.countedToFilters(options.comicCharacters);
      case 'comicTeam':
        return this.countedToFilters(options.comicTeams);
      case 'comicLocation':
        return this.countedToFilters(options.comicLocations);
      case 'comicCreator':
        return (options.comicCreators ?? []).map(item => {
          const parts = item.name.split(':');
          const role = parts.pop()!;
          const name = parts.join(':');
          return {
            value: {id: item.name, name: `${name} (${role})`},
            bookCount: item.count
          };
        });
      case 'shelf':
        return (options.shelves ?? []).map(item => {
          const [id, ...nameParts] = item.name.split(':');
          return {
            value: {id: Number(id), name: nameParts.join(':')},
            bookCount: item.count
          };
        });
      case 'library':
        return (options.libraries ?? []).map(item => {
          const [id, ...nameParts] = item.name.split(':');
          return {
            value: {id: Number(id), name: nameParts.join(':')},
            bookCount: item.count
          };
        });
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

  private rangedFilters(
    items: CountedOption[] | undefined,
    ranges: readonly {id: number; label: string; sortIndex: number}[]
  ): Filter[] {
    return (items ?? []).map(item => {
      const rangeId = Number(item.name);
      const range = ranges.find(r => r.id === rangeId);
      return {
        value: {id: rangeId, name: range?.label || item.name, sortIndex: range?.sortIndex},
        bookCount: item.count
      };
    });
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
