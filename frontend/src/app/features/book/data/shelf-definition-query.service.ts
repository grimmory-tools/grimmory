import {HttpClient} from '@angular/common/http';
import {effect, inject, Injectable} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';
import {lastValueFrom, takeUntil} from 'rxjs';

import {API_CONFIG} from '../../../core/config/api-config';
import {abortSignal, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {ShelfDefinition} from './shelf-definition.models';
import {shelfDefinitionQueryKeys} from './shelf-definition-query-keys';
import {AuthService} from '../../../shared/service/auth.service';

@Injectable({providedIn: 'root'})
export class ShelfDefinitionQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/shelves`;

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: shelfDefinitionQueryKeys.all()});
      }
    });
  }

  definitions() {
    return queryOptions({
      queryKey: shelfDefinitionQueryKeys.definitions(),
      queryFn: ({signal}): Promise<ShelfDefinition[]> => lastValueFrom(
        this.http.get<ShelfDefinition[]>(this.url).pipe(
          takeUntil(abortSignal(signal)),
        ),
      ),
      ...QUERY_DEFAULTS,
    });
  }
}
