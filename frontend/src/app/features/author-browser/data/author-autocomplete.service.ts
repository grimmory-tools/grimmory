import {computed, inject, Injectable, signal} from '@angular/core';
import type {ScrollerOptions} from '@openng/optimus-ui/api';
import type {ScrollerScrollEvent} from '@openng/optimus-ui/types/scroller';
import {injectInfiniteQuery} from '@tanstack/angular-query-experimental';

import {AuthorQueryService} from './author-query.service';
import {DEFAULT_AUTHOR_SORT_TERMS, EMPTY_AUTHOR_FACET_SELECTION} from './author-query-params';

const PAGE_SIZE = 20;

@Injectable()
export class AuthorAutocompleteService {
  private readonly authorQueryService = inject(AuthorQueryService);
  private readonly searchTerm = signal('');
  private readonly query = injectInfiniteQuery(() => {
    const query = this.searchTerm();
    return {
      ...this.authorQueryService.infinitePage({
        query,
        facets: EMPTY_AUTHOR_FACET_SELECTION,
        facetLogic: 'and',
        sort: DEFAULT_AUTHOR_SORT_TERMS,
        size: PAGE_SIZE,
      }),
      enabled: query.length > 0,
    };
  });

  readonly suggestions = computed(() => {
    if (this.searchTerm().length === 0 || this.query.isError()) return [];
    return this.query.data()?.pages.flatMap(page => page.content.map(author => author.name)) ?? [];
  });
  readonly virtualScrollOptions: ScrollerOptions = {
    onScroll: (event: ScrollerScrollEvent) => this.loadMore(event),
  };

  search(query: string): void {
    this.searchTerm.set(query.trim());
  }

  reset(): void {
    this.searchTerm.set('');
  }

  private loadMore(event: ScrollerScrollEvent): void {
    const element = event.originalEvent?.target as HTMLElement | null;
    if (!element || element.scrollTop + element.clientHeight < element.scrollHeight - 1) return;
    if (!this.query.hasNextPage() || this.query.isFetchingNextPage()) return;
    void this.query.fetchNextPage();
  }
}
