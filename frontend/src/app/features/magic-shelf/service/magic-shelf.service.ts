import {computed, effect, inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom, Observable} from 'rxjs';
import {tap} from 'rxjs/operators';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {BookService} from '../../book/service/book.service';
import {BookRuleEvaluatorService} from './book-rule-evaluator.service';
import {AuthService} from '../../../shared/service/auth.service';
import {GroupRule} from '../component/magic-shelf-component';

export interface MagicShelf {
  id?: number | null;
  name: string;
  icon?: string | null;
  iconType?: 'PRIME_NG' | 'CUSTOM_SVG' | null;
  filterJson: string;
  isPublic?: boolean;
}

const MAGIC_SHELVES_QUERY_KEY = ['magicShelves'] as const;

@Injectable({
  providedIn: 'root',
})
export class MagicShelfService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/magic-shelves`;

  private readonly http = inject(HttpClient);
  private readonly bookService = inject(BookService);
  private readonly ruleEvaluatorService = inject(BookRuleEvaluatorService);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly token = this.authService.token;

  private readonly shelvesQuery = injectQuery(() => ({
    ...this.getShelvesQueryOptions(),
    enabled: !!this.token(),
  }));

  readonly shelves = computed(() => this.shelvesQuery.data() ?? []);

  readonly shelvesError = computed<string | null>(() => {
    if (!this.token() || !this.shelvesQuery.isError()) {
      return null;
    }

    const error = this.shelvesQuery.error();
    return error instanceof Error ? error.message : 'Failed to load magic shelves';
  });

  readonly isShelvesLoading = computed(() => !!this.token() && this.shelvesQuery.isPending());

  constructor() {
    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: MAGIC_SHELVES_QUERY_KEY});
      }
    });
  }

  private getShelvesQueryOptions() {
    return queryOptions({
      queryKey: MAGIC_SHELVES_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<MagicShelf[]>(this.url))
    });
  }

  saveShelf(data: {
    id?: number;
    name: string | null;
    icon: string | null;
    iconType?: 'PRIME_NG' | 'CUSTOM_SVG';
    group: unknown;
    isPublic?: boolean | null;
  }): Observable<MagicShelf> {
    const payload: MagicShelf = {
      id: data.id,
      name: data.name ?? '',
      icon: data.icon,
      iconType: data.iconType,
      filterJson: JSON.stringify(data.group),
      isPublic: data.isPublic ?? false
    };

    return this.http.post<MagicShelf>(this.url, payload).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: MAGIC_SHELVES_QUERY_KEY, exact: true});
      })
    );
  }

  findShelfById(id: number): MagicShelf | undefined {
    return this.shelves().find(shelf => shelf.id === id);
  }

  getBookCountValue(shelfId: number): number {
    const shelf = this.findShelfById(shelfId);
    if (!shelf) {
      return 0;
    }

    let group: GroupRule;
    try {
      group = JSON.parse(shelf.filterJson);
    } catch (error) {
      console.error('Invalid filter JSON', error);
      return 0;
    }

    const allBooks = this.bookService.books();
    return allBooks.filter(book =>
      this.ruleEvaluatorService.evaluateGroup(book, group, allBooks)
    ).length;
  }

  readonly bookCountByMagicShelfId = computed(() => {
    const counts = new Map<number, number>();
    for (const shelf of this.shelves()) {
      if (shelf.id != null) {
        counts.set(shelf.id, this.getBookCountValue(shelf.id));
      }
    }
    return counts;
  });

  deleteShelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: MAGIC_SHELVES_QUERY_KEY, exact: true});
      })
    );
  }
}
