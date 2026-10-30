import {Directive, inject, input} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AutoComplete} from '@openng/optimus-ui/autocomplete';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {catchError, from, map, of, switchMap} from 'rxjs';

import {SEARCH_DEBOUNCE_MS} from '../../../shared/util/search-terms';
import {type BookCollectionFilterParams, type BookQueryFacetKey, EMPTY_FACET_SELECTION} from '../data/book-query-params';
import {BookQueryService} from '../data/book-query.service';

const ALL_BOOKS: BookCollectionFilterParams = {facets: EMPTY_FACET_SELECTION, facetLogic: 'or'};

@Directive({selector: '[appFacetSuggestions]'})
export class FacetSuggestionsDirective {
  private readonly autoComplete = inject(AutoComplete);
  private readonly bookQuery = inject(BookQueryService);
  private readonly queryClient = inject(QueryClient);
  readonly facet = input.required<BookQueryFacetKey>({alias: 'appFacetSuggestions'});

  constructor() {
    this.autoComplete.delay = SEARCH_DEBOUNCE_MS;

    this.autoComplete.completeMethod.pipe(
      switchMap(({query}) => from(this.queryClient.query(this.bookQuery.facet(this.facet(), ALL_BOOKS, query))).pipe(
        map(group => group.values.map(({value}) => value)),
        catchError(() => of([])),
      )),
      takeUntilDestroyed(),
    ).subscribe(values => this.autoComplete.suggestions = values);
  }
}
