import {HttpClient} from '@angular/common/http';
import {effect, inject, Service} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {httpGet, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {shelfQueryKeys} from './shelf-query-keys';
import type {MagicShelf, Shelf} from './shelf-query.models';

@Service()
export class ShelfQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly shelvesUrl = `${API_CONFIG.BASE_URL}/api/v1/shelves`;
  private readonly magicShelvesUrl = `${API_CONFIG.BASE_URL}/api/magic-shelves`;

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: shelfQueryKeys.all()});
      }
    });
  }

  shelves() {
    return queryOptions({
      queryKey: shelfQueryKeys.shelves(),
      queryFn: ({signal}) => httpGet<readonly Shelf[]>(this.http, this.shelvesUrl, signal),
      ...QUERY_DEFAULTS,
    });
  }

  magicShelves() {
    return queryOptions({
      queryKey: shelfQueryKeys.magicShelves(),
      queryFn: ({signal}) => httpGet<readonly MagicShelf[]>(this.http, this.magicShelvesUrl, signal),
      ...QUERY_DEFAULTS,
    });
  }
}
