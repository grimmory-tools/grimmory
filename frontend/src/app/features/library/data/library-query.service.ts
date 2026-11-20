import {HttpClient} from '@angular/common/http';
import {effect, inject, Service} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {httpGet, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {libraryQueryKeys} from './library-query-keys';
import type {Library} from './library-query.models';

@Service()
export class LibraryQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly librariesUrl = `${API_CONFIG.BASE_URL}/api/v1/libraries`;
  private readonly pathUrl = `${API_CONFIG.BASE_URL}/api/v1/path`;

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: libraryQueryKeys.all()});
      }
    });
  }

  libraries() {
    return queryOptions({
      queryKey: libraryQueryKeys.libraries(),
      queryFn: async ({signal}): Promise<readonly Library[]> => {
        const libraries = await httpGet<readonly Library[]>(this.http, this.librariesUrl, signal);
        return [...libraries].sort((a, b) => a.name.localeCompare(b.name));
      },
      ...QUERY_DEFAULTS,
    });
  }

  formatCounts(libraryId: number) {
    return queryOptions({
      queryKey: libraryQueryKeys.formatCounts(libraryId),
      queryFn: ({signal}) => httpGet<Readonly<Record<string, number>>>(
        this.http,
        `${this.librariesUrl}/${libraryId}/format-counts`,
        signal,
      ),
      ...QUERY_DEFAULTS,
    });
  }

  health() {
    return queryOptions({
      queryKey: libraryQueryKeys.health(),
      queryFn: ({signal}) => httpGet<Readonly<Record<number, boolean>>>(this.http, `${this.librariesUrl}/health`, signal),
      ...QUERY_DEFAULTS,
      staleTime: Infinity,
    });
  }

  folders(path: string) {
    return queryOptions({
      queryKey: libraryQueryKeys.folders(path),
      queryFn: ({signal}) => httpGet<readonly string[]>(this.http, this.pathUrl, signal, {path}),
      ...QUERY_DEFAULTS,
    });
  }
}
