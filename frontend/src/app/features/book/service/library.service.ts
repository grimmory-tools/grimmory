import {computed, effect, inject, Injectable, signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {from, lastValueFrom, Observable} from 'rxjs';
import {tap} from 'rxjs/operators';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {Library} from '../model/library.model';
import {BookService} from './book.service';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {LIBRARIES_QUERY_KEY, libraryFormatCountsQueryKey} from './library-query-keys';

@Injectable({providedIn: 'root'})
export class LibraryService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/libraries`;
  private http = inject(HttpClient);
  private bookService = inject(BookService);
  private authService = inject(AuthService);
  private queryClient = inject(QueryClient);
  private readonly token = this.authService.token;

  readonly largeLibraryLoading = signal<{ isLoading: boolean; expectedCount: number }>({
    isLoading: false,
    expectedCount: 0
  });

  private librariesQuery = injectQuery(() => ({
    ...this.getLibrariesQueryOptions(),
    enabled: !!this.token(),
  }));

  libraries = computed(() => this.librariesQuery.data() ?? []);

  librariesError = computed<string | null>(() => {
    if (!this.token() || !this.librariesQuery.isError()) {
      return null;
    }

    const error = this.librariesQuery.error();
    return error instanceof Error ? error.message : 'Failed to load libraries';
  });

  isLibrariesLoading = computed(() => !!this.token() && this.librariesQuery.isPending());

  constructor() {
    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: LIBRARIES_QUERY_KEY});
      }
    });
  }

  private getLibrariesQueryOptions() {
    return queryOptions({
      queryKey: LIBRARIES_QUERY_KEY,
      queryFn: async () => {
        const libraries = await lastValueFrom(this.http.get<Library[]>(this.url));
        return this.sortLibraries(libraries);
      }
    });
  }

  private getLibraryFormatCountsQueryOptions(libraryId: number) {
    return queryOptions({
      queryKey: libraryFormatCountsQueryKey(libraryId),
      queryFn: () => lastValueFrom(this.http.get<Record<string, number>>(`${this.url}/${libraryId}/format-counts`))
    });
  }

  scanLibraryPaths(lib: Library): Observable<number> {
    return this.http.post<number>(`${this.url}/scan`, lib);
  }

  createLibrary(lib: Library): Observable<Library> {
    return this.http.post<Library>(this.url, lib).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: LIBRARIES_QUERY_KEY, exact: true});
      })
    );
  }

  updateLibrary(lib: Library, id?: number): Observable<Library> {
    return this.http.put<Library>(`${this.url}/${id}`, lib).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: LIBRARIES_QUERY_KEY, exact: true});
        void this.queryClient.invalidateQueries({queryKey: BOOKS_QUERY_KEY, exact: true});
      })
    );
  }

  deleteLibrary(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: LIBRARIES_QUERY_KEY, exact: true});
        void this.queryClient.invalidateQueries({queryKey: BOOKS_QUERY_KEY, exact: true});
        this.queryClient.removeQueries({queryKey: libraryFormatCountsQueryKey(id), exact: true});
      })
    );
  }

  refreshLibrary(id: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/refresh`, {}).pipe(
      tap(() => {
        void this.queryClient.invalidateQueries({queryKey: LIBRARIES_QUERY_KEY, exact: true});
      })
    );
  }

  updateLibraryFileNamingPattern(id: number, pattern: string): Observable<Library> {
    return this.http
      .patch<Library>(`${this.url}/${id}/file-naming-pattern`, {fileNamingPattern: pattern})
      .pipe(
        tap(() => {
          void this.queryClient.invalidateQueries({queryKey: LIBRARIES_QUERY_KEY, exact: true});
        })
      );
  }

  doesLibraryExistByName(name: string): boolean {
    return this.libraries().some(library => library.name === name);
  }

  findLibraryById(id: number): Library | undefined {
    return this.libraries().find(library => library.id === id);
  }

  getBookCountValue(libraryId: number): number {
    return this.bookService.books().filter(book => book.libraryId === libraryId).length;
  }

  readonly bookCountByLibraryId = computed(() => {
    const counts = new Map<number, number>();
    for (const book of this.bookService.books()) {
      if (book.libraryId != null) {
        counts.set(book.libraryId, (counts.get(book.libraryId) ?? 0) + 1);
      }
    }
    return counts;
  });

  setLargeLibraryLoading(isLoading: boolean, expectedCount: number): void {
    this.largeLibraryLoading.set({ isLoading, expectedCount });
  }

  getBookCountsByFormat(libraryId: number): Observable<Record<string, number>> {
    return from(this.queryClient.ensureQueryData(this.getLibraryFormatCountsQueryOptions(libraryId)));
  }

  private sortLibraries(libraries: Library[]): Library[] {
    return [...libraries].sort((a, b) => a.name.localeCompare(b.name));
  }

}
