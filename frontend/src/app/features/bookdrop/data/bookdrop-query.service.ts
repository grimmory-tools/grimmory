import {HttpClient} from '@angular/common/http';
import {effect, inject, Service} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {httpGet, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import {UserService} from '../../settings/user-management/user.service';
import {bookdropQueryKeys} from './bookdrop-query-keys';
import type {BookdropFileNotification, BookdropFilePage, BookdropFilesParams} from './bookdrop-query.models';

@Service()
export class BookdropQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly queryClient = inject(QueryClient);
  private readonly bookdropUrl = `${API_CONFIG.BASE_URL}/api/v1/bookdrop`;

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: bookdropQueryKeys.all()});
      }
    });
  }

  summary() {
    const permissions = this.userService.currentUser()?.permissions;
    return queryOptions({
      queryKey: bookdropQueryKeys.summary(),
      queryFn: ({signal}) => httpGet<BookdropFileNotification>(this.http, `${this.bookdropUrl}/notification`, signal),
      ...QUERY_DEFAULTS,
      staleTime: Infinity,
      enabled: !!permissions && (permissions.admin || permissions.canAccessBookdrop),
    });
  }

  pendingFiles({page, size}: BookdropFilesParams) {
    return queryOptions({
      queryKey: bookdropQueryKeys.pendingFiles({page, size}),
      queryFn: ({signal}) =>
        httpGet<BookdropFilePage>(this.http, `${this.bookdropUrl}/files`, signal, {status: 'pending', page, size}),
      ...QUERY_DEFAULTS,
    });
  }
}
