import {Injectable} from '@angular/core';
import {Book} from '../model/book.model';
import {SortDirection, SortOption} from "../model/sort.model";

@Injectable({
  providedIn: 'root',
})
export class SortService {

  private readonly fieldExtractors: Record<string, (book: Book) => any> = {
    title: (book) => book.metadata?.title?.toLowerCase() || null,
    author: (book) => book.metadata?.authors?.map(a => a.toLowerCase()).join(", ") || null,
    publishedDate: (book) => book.metadata?.publishedDate === null ? null : new Date(book.metadata?.publishedDate!).getTime(),
    publisher: (book) => book.metadata?.publisher || null,
    pageCount: (book) => book.metadata?.pageCount || null,
    rating: (book) => book.metadata?.rating || null,
    reviewCount: (book) => book.metadata?.reviewCount || null,
    amazonRating: (book) => book.metadata?.amazonRating || null,
    amazonReviewCount: (book) => book.metadata?.amazonReviewCount || null,
    goodreadsRating: (book) => book.metadata?.goodreadsRating || null,
    goodreadsReviewCount: (book) => book.metadata?.goodreadsReviewCount || null,
    locked: (book) =>
      Object.keys(book.metadata ?? {})
        .filter((key) => key.endsWith('Locked'))
        .every((key) => book.metadata?.[key] === true),
    lastReadTime: (book) => book.lastReadTime ? new Date(book.lastReadTime).getTime() : null,
    addedOn: (book) => book.addedOn ? new Date(book.addedOn).getTime() : null,
    fileSizeKb: (book) => book.fileSizeKb || null
  };

  applySort(books: Book[], selectedSort: SortOption | null): Book[] {
    if (!selectedSort) return books;

    const {field, direction} = selectedSort;
    const extractor = this.fieldExtractors[field];

    if (!extractor) return books;

    return books.sort((a, b) => {
      const valueA = extractor(a);
      const valueB = extractor(b);

      if (valueA === null || valueA === undefined) return 1;
      if (valueB === null || valueB === undefined) return -1;

      if (direction === SortDirection.ASCENDING) {
        return valueA < valueB ? -1 : valueA > valueB ? 1 : 0;
      } else {
        return valueA > valueB ? -1 : valueA < valueB ? 1 : 0;
      }
    });
  }
}
