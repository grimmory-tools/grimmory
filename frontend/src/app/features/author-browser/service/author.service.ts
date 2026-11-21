import {computed, DestroyRef, effect, inject, Service} from '@angular/core';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {lastValueFrom, Observable, of} from 'rxjs';
import {map, switchMap, tap} from 'rxjs/operators';
import {SseClient} from 'ngx-sse-client';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthorSummary, AuthorDetails, AuthorSearchResult, AuthorMatchRequest, AuthorMatchResolution, AuthorUpdateRequest, AuthorPhotoResult} from '../model/author.model';
import {Book} from '../../book/model/book.model';
import {AuthService} from '../../../shared/service/auth.service';
import {injectQuery, queryOptions, QueryClient} from '@tanstack/angular-query';
import {AUTHORS_QUERY_KEY} from './author-query-keys';
import {invalidateAuthorsQuery, patchAuthorInCache} from './author-query-cache';
import {invalidateAllBookCaches} from '../../book/service/legacy-book-cache';

@Service()
export class AuthorService {

  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly sseClient = inject(SseClient);
  private readonly queryClient = inject(QueryClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/authors`;
  private readonly mediaBaseUrl = `${API_CONFIG.BASE_URL}/api/v1/media`;
  private readonly token = this.authService.token;
  private readonly authorInvalidationDebounceMs = 200;
  private authorInvalidationTimer: ReturnType<typeof setTimeout> | null = null;

  private readonly authorsQuery = injectQuery(() => ({
    ...this.getAuthorsQueryOptions(),
    enabled: !!this.token(),
  }));

  readonly allAuthors = computed(() => this.authorsQuery.data() ?? null);
  readonly isAuthorsLoading = computed(() => !!this.token() && this.authorsQuery.isPending());

  constructor() {
    this.destroyRef.onDestroy(() => this.clearAuthorInvalidationTimer());

    effect(() => {
      const token = this.token();
      if (token === null) {
        this.queryClient.removeQueries({queryKey: AUTHORS_QUERY_KEY});
      }
    });
  }

  private getAuthorsQueryOptions() {
    return queryOptions({
      queryKey: AUTHORS_QUERY_KEY,
      queryFn: () => lastValueFrom(this.http.get<AuthorSummary[]>(this.baseUrl))
    });
  }

  invalidateAuthors(): void {
    invalidateAuthorsQuery(this.queryClient);
  }

  async refreshAuthors(): Promise<AuthorSummary[]> {
    await this.queryClient.cancelQueries({queryKey: AUTHORS_QUERY_KEY, exact: true});
    return this.queryClient.fetchQuery({...this.getAuthorsQueryOptions(), staleTime: 0});
  }

  handleNewlyCreatedBook(book: Book): void {
    if (!book.metadata?.authors?.length) {
      return;
    }
    this.debouncedInvalidateAuthors();
  }

  private debouncedInvalidateAuthors(): void {
    this.clearAuthorInvalidationTimer();

    this.authorInvalidationTimer = setTimeout(() => {
      this.authorInvalidationTimer = null;
      this.invalidateAuthors();
    }, this.authorInvalidationDebounceMs);
  }

  private clearAuthorInvalidationTimer(): void {
    if (this.authorInvalidationTimer === null) {
      return;
    }

    clearTimeout(this.authorInvalidationTimer);
    this.authorInvalidationTimer = null;
  }

  patchAuthorInCache(authorId: number, fields: Partial<AuthorSummary>): void {
    patchAuthorInCache(this.queryClient, authorId, fields);
  }

  getAuthorDetails(authorId: number): Observable<AuthorDetails> {
    return this.http.get<AuthorDetails>(`${this.baseUrl}/${authorId}`);
  }

  getAuthorByName(name: string): Observable<AuthorDetails> {
    return this.http.get<AuthorDetails>(`${this.baseUrl}/by-name`, {params: {name}});
  }

  searchAuthorMetadata(authorId: number, query: string, region?: string, asin?: string): Observable<AuthorSearchResult[]> {
    const params: Record<string, string> = {};
    if (region) {
      params['region'] = region;
    }
    if (asin) {
      params['asin'] = asin;
    } else {
      params['q'] = query;
    }
    return this.http.get<AuthorSearchResult[]>(`${this.baseUrl}/${authorId}/search-metadata`, {params});
  }

  resolveAuthorMatch(authorId: number, authorName: string, region?: string, asin?: string): Observable<AuthorMatchResolution> {
    const searchByName = () => this.searchAuthorMetadata(authorId, authorName, region);
    const search$ = asin
      ? this.searchAuthorMetadata(authorId, authorName, region, asin).pipe(
        switchMap(results => results.length > 0 ? of(results) : searchByName())
      )
      : searchByName();

    return search$.pipe(
      map(results => {
        const uniqueCandidates = new Map<string, AuthorSearchResult>();
        for (const result of results) {
          const candidateId = result.providerId ?? result.asin ?? result.name;
          if (!uniqueCandidates.has(candidateId)) {
            uniqueCandidates.set(candidateId, result);
          }
        }
        return [...uniqueCandidates.values()];
      }),
      switchMap(candidates => {
        if (candidates.length === 0) {
          return of({status: 'not-found'} as const);
        }
        if (candidates.length > 1) {
          return of({status: 'ambiguous', candidates} as const);
        }
        const candidate = candidates[0];
        return this.matchAuthor(authorId, {
          source: candidate.source,
          providerId: candidate.providerId ?? candidate.asin,
          asin: candidate.asin,
          region,
        }).pipe(map(author => ({status: 'matched', author, candidate} as const)));
      })
    );
  }

  matchAuthor(authorId: number, request: AuthorMatchRequest): Observable<AuthorDetails> {
    return this.http.post<AuthorDetails>(`${this.baseUrl}/${authorId}/match`, request);
  }

  quickMatchAuthor(authorId: number, region?: string): Observable<AuthorDetails> {
    return this.http.post<AuthorDetails>(`${this.baseUrl}/${authorId}/quick-match`, null, {
      params: region ? {region} : {},
    });
  }

  autoMatchAuthors(authorIds: number[]): Observable<AuthorSummary> {
    const token = this.authService.getInternalAccessToken();
    const headers = new HttpHeaders()
      .set('Content-Type', 'application/json')
      .set('Authorization', `Bearer ${token}`);

    return this.sseClient.stream(
      `${this.baseUrl}/auto-match`,
      {keepAlive: false, reconnectionDelay: 1000, responseType: 'event'},
      {headers, body: authorIds, withCredentials: true},
      'POST'
    ).pipe(
      map(event => {
        if (event.type === 'error') {
          throw new Error((event as ErrorEvent).message);
        }
        return JSON.parse((event as MessageEvent).data) as AuthorSummary;
      })
    );
  }

  updateAuthor(authorId: number, request: AuthorUpdateRequest): Observable<AuthorDetails> {
    return this.http.put<AuthorDetails>(`${this.baseUrl}/${authorId}`, request).pipe(
      tap(() => invalidateAllBookCaches(this.queryClient))
    );
  }

  deleteAuthorPhoto(authorId: number): Observable<AuthorDetails> {
    return this.http.delete<AuthorDetails>(`${this.baseUrl}/${authorId}/photo`);
  }

  unmatchAuthors(authorIds: number[]): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/unmatch`, authorIds);
  }

  deleteAuthors(authorIds: number[]): Observable<void> {
    return this.http.delete<void>(this.baseUrl, {body: authorIds}).pipe(
      tap(() => {
        invalidateAllBookCaches(this.queryClient);
        invalidateAuthorsQuery(this.queryClient);
      })
    );
  }

  deleteAuthorsWithoutBooks(): Observable<number> {
    return this.http.delete<number>(`${this.baseUrl}/without-books`).pipe(
      tap(() => invalidateAuthorsQuery(this.queryClient))
    );
  }

  searchAuthorPhotos(authorId: number, query: string): Observable<AuthorPhotoResult> {
    const token = this.authService.getInternalAccessToken();
    const headers = new HttpHeaders()
      .set('Content-Type', 'application/json')
      .set('Authorization', `Bearer ${token}`);

    return this.sseClient.stream(
      `${this.baseUrl}/${authorId}/search-photos`,
      {keepAlive: false, reconnectionDelay: 1000, responseType: 'event'},
      {headers, params: {q: query}, withCredentials: true},
      'GET'
    ).pipe(
      map(event => {
        if (event.type === 'error') {
          throw new Error((event as ErrorEvent).message);
        }
        return JSON.parse((event as MessageEvent).data) as AuthorPhotoResult;
      })
    );
  }

  uploadAuthorPhotoFromUrl(authorId: number, imageUrl: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${authorId}/photo/url`, null, {
      params: {url: imageUrl}
    });
  }

  getUploadAuthorPhotoUrl(authorId: number): string {
    return `${this.baseUrl}/${authorId}/photo/upload`;
  }

  getAuthorPhotoUrl(authorId: number, cacheBuster?: number): string {
    const token = this.authService.getInternalAccessToken();
    let url = `${this.mediaBaseUrl}/author/${authorId}/photo`;
    if (token) {
      url += `?token=${token}`;
    }
    if (cacheBuster) {
      url += (token ? '&' : '?') + 't=' + cacheBuster;
    }
    return url;
  }

  getAuthorThumbnailUrl(authorId: number, cacheBuster?: number): string {
    const token = this.authService.getInternalAccessToken();
    let url = `${this.mediaBaseUrl}/author/${authorId}/thumbnail`;
    if (token) {
      url += `?token=${token}`;
    }
    if (cacheBuster) {
      url += (token ? '&' : '?') + 't=' + cacheBuster;
    }
    return url;
  }
}
