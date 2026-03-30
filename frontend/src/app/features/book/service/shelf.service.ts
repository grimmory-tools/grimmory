import {computed, effect, inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {lastValueFrom, Observable} from 'rxjs';
import {tap} from 'rxjs/operators';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {Shelf} from '../model/shelf.model';
import {BookService} from './book.service';
import {API_CONFIG} from '../../../core/config/api-config';
import {Book} from '../model/book.model';
import {UserService} from '../../settings/user-management/user.service';
import {AuthService} from '../../../shared/service/auth.service';

const SHELVES_QUERY_KEY = ['shelves'] as const;

@Injectable({providedIn: 'root'})
export class ShelfService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/shelves`;
  private http = inject(HttpClient);
  private bookService = inject(BookService);
  private userService = inject(UserService);
  private authService = inject(AuthService);
  private queryClient = inject(QueryClient);
  private readonly token = this.authService.token;

  private shelvesQuery = injectQuery(() => ({
    ...this.getShelvesQueryOptions(),
    enabled: !!this.token(),
  }));

  shelves = computed(() => this.shelvesQuery.data() ?? []);

  shelvesError = computed<string | null>(() => {
    if (!this.token() || !this.shelvesQuery.isError()) {
      return null;
    }

    const error = this.shelvesQuery.error();
    return error instanceof Error ? error.message : 'Failed to load shelves';
  });

  isShelvesLoading = computed(() => !!this.token() && this.shelvesQuery.isPending());

  constructor() {
    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: SHELVES_QUERY_KEY});
      }
    });
  }

  private getShelvesQueryOptions() {
    return queryOptions({
      queryKey: SHELVES_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<Shelf[]>(this.url))
    });
  }

  reloadShelves(): void {
    void this.queryClient.invalidateQueries({queryKey: SHELVES_QUERY_KEY, exact: true});
  }

  createShelf(shelf: Shelf): Observable<Shelf> {
    return this.http.post<Shelf>(this.url, shelf).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: SHELVES_QUERY_KEY, exact: true});
      })
    );
  }

  updateShelf(shelf: Shelf, id?: number): Observable<Shelf> {
    return this.http.put<Shelf>(`${this.url}/${id}`, shelf).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: SHELVES_QUERY_KEY, exact: true});
      })
    );
  }

  deleteShelf(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => {
        this.bookService.removeBooksFromShelf(id);
        void this.queryClient.invalidateQueries({queryKey: SHELVES_QUERY_KEY, exact: true});
      })
    );
  }

  getBookCountValue(shelfId: number): number {
    const shelf = this.shelves().find(currentShelf => currentShelf.id === shelfId);
    if (!shelf) return 0;

    const currentUserId = this.userService.getCurrentUser()?.id;
    const isOwner = currentUserId === shelf.userId;

    if (isOwner) {
      return this.bookService.books().filter(book =>
        book.shelves?.some(currentShelf => currentShelf.id === shelfId)
      ).length;
    }

    return shelf.bookCount || 0;
  }

  getBooksOnShelf(shelfId: number): Observable<Book[]> {
    return this.http.get<Book[]>(`${this.url}/${shelfId}/books`);
  }

  getUnshelvedBookCountValue(): number {
    return this.bookService.books().filter(book => !book.shelves || book.shelves.length === 0).length;
  }

}
