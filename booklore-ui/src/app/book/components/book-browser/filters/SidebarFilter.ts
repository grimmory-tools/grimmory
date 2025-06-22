import {Observable, combineLatest, of} from 'rxjs';
import {map} from 'rxjs/operators';
import {BookFilter} from './BookFilter';
import {BookState} from '../../../model/state/book-state.model';
import {fileSizeRanges, matchScoreRanges, pageCountRanges, ratingRanges, ratingRanges10} from '../book-filter/book-filter.component';

export function isRatingInRange(rating: number | undefined | null, rangeId: string): boolean {
  if (rating == null) return false;
  const range = ratingRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return rating >= range.min && rating < range.max;
}

export function isRatingInRange10(rating: number | undefined | null, rangeId: string): boolean {
  if (rating == null) return false;
  const range = ratingRanges10.find(r => r.id === rangeId);
  if (!range) return false;
  return rating >= range.min && rating < range.max;
}

export function isFileSizeInRange(fileSizeKb: number | undefined, rangeId: string): boolean {
  if (fileSizeKb == null) return false;
  const range = fileSizeRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return fileSizeKb >= range.min && fileSizeKb < range.max;
}

export function isPageCountInRange(pageCount: number | undefined, rangeId: string): boolean {
  if (pageCount == null) return false;
  const range = pageCountRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return pageCount >= range.min && pageCount < range.max;
}

export function isMatchScoreInRange(score: number | undefined | null, rangeId: string): boolean {
  if (score == null) return false;
  const range = matchScoreRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return score >= range.min && score < range.max;
}

export class SideBarFilter implements BookFilter {

  constructor(private selectedFilter$: Observable<any>, private selectedFilterMode$: Observable<'and' | 'or'>) {
  }

  filter(bookState: BookState): Observable<BookState> {
    return combineLatest([this.selectedFilter$, this.selectedFilterMode$]).pipe(
      map(([activeFilters, mode]) => {
        if (!activeFilters) return bookState;
        const filteredBooks = (bookState.books || []).filter(book => {
          const matches = Object.entries(activeFilters).map(([filterType, filterValues]) => {
            if (!Array.isArray(filterValues) || filterValues.length === 0) {
              return mode === 'or';
            }
            switch (filterType) {
              case 'author':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.authors?.includes(val))
                  : filterValues.some(val => book.metadata?.authors?.includes(val));
              case 'category':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.categories?.includes(val))
                  : filterValues.some(val => book.metadata?.categories?.includes(val));
              case 'publisher':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.publisher === val)
                  : filterValues.some(val => book.metadata?.publisher === val);
              case 'series':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.seriesName === val)
                  : filterValues.some(val => book.metadata?.seriesName === val);
              case 'readStatus':
                return filterValues.some(val => book.readStatus === val);
              case 'amazonRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.amazonRating, range));
              case 'goodreadsRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.goodreadsRating, range));
              case 'hardcoverRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.hardcoverRating, range));
              case 'personalRating':
                return filterValues.some(range => isRatingInRange10(book.metadata?.personalRating, range));
              case 'publishedDate':
                return filterValues.includes(new Date(book.metadata?.publishedDate || '').getFullYear());
              case 'fileSize':
                return filterValues.some(range => isFileSizeInRange(book.fileSizeKb, range));
              case 'shelfStatus':
                const shelved = book.shelves && book.shelves.length > 0 ? 'shelved' : 'unshelved';
                return filterValues.includes(shelved);
              case 'pageCount':
                return filterValues.some(range => isPageCountInRange(book.metadata?.pageCount!, range));
              case 'language':
                return filterValues.includes(book.metadata?.language);
              case 'matchScore':
                return filterValues.some(range => isMatchScoreInRange(book.metadataMatchScore, range));
              default:
                return false;
            }
          });
          return mode === 'and' ? matches.every(m => m) : matches.some(m => m);
        });
        return {...bookState, books: filteredBooks};
      })
    );
  }
}
