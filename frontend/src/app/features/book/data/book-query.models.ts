import {InfiniteData} from '@tanstack/angular-query-experimental';

import {BrowseFacetGroup, BrowsePage} from '../../../core/data/browse.models';
import {BookSummary} from './book-response.models';

export type BookPage = BrowsePage<BookSummary>;
export type BookFacetGroup = BrowseFacetGroup;

export function flattenBookPages(
  data: InfiniteData<BookPage> | undefined,
): BookSummary[] {
  return data?.pages.flatMap(page => page.content) ?? [];
}
