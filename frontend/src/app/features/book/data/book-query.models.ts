import {InfiniteData} from '@tanstack/angular-query-experimental';

import {BrowseFacetGroup, BrowsePage} from '../../../core/data/browse.models';
import {BookSummary} from './book-response.models';

export type BookPage = BrowsePage<BookSummary>;
export type BookFacetGroup = BrowseFacetGroup;

export function flattenBookPages(
  data: InfiniteData<BookPage> | undefined,
): BookSummary[] {
  const books = data?.pages.flatMap(page => page.content) ?? [];
  const seen = new Set<number>();
  return books.filter(book => {
    if (seen.has(book.id)) {
      return false;
    }
    seen.add(book.id);
    return true;
  });
}
