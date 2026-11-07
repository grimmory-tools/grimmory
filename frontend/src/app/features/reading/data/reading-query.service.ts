import {HttpClient} from '@angular/common/http';
import {effect, inject, Service} from '@angular/core';
import {queryOptions, QueryClient} from '@tanstack/angular-query-experimental';

import {API_CONFIG} from '../../../core/config/api-config';
import {httpGet, QUERY_DEFAULTS} from '../../../core/data/query-transport';
import {AuthService} from '../../../shared/service/auth.service';
import type {BookFileType} from '../../book/data/book-response.models';
import {readingQueryKeys} from './reading-query-keys';
import {
  NOTEBOOK_ENTRY_TYPES,
  type Annotation,
  type AudiobookInfo,
  type Bookmark,
  type BookNote,
  type ComicPageDimensions,
  type ComicPageInfo,
  type EpubInfo,
  type Note,
  type NotebookBook,
  type NotebookEntry,
  type NotebookFilterParams,
  type NotebookPage,
  type NotebookPageParams,
  type ReadingSessionPage,
  type ReadingSessionPageParams,
  type ViewerSettings,
} from './reading-query.models';

@Service()
export class ReadingQueryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);
  private readonly queryClient = inject(QueryClient);
  private readonly annotationsUrl = `${API_CONFIG.BASE_URL}/api/v1/annotations`;
  private readonly bookmarksUrl = `${API_CONFIG.BASE_URL}/api/v1/bookmarks`;
  private readonly notesUrl = `${API_CONFIG.BASE_URL}/api/v2/book-notes`;
  private readonly bookNotesUrl = `${API_CONFIG.BASE_URL}/api/v1/book-notes`;
  private readonly pdfAnnotationsUrl = `${API_CONFIG.BASE_URL}/api/v1/pdf-annotations`;
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private readonly readingSessionsUrl = `${API_CONFIG.BASE_URL}/api/v1/reading-sessions`;
  private readonly notebookUrl = `${API_CONFIG.BASE_URL}/api/v1/notebook`;
  private readonly epubUrl = `${API_CONFIG.BASE_URL}/api/v1/epub`;
  private readonly cbxUrl = `${API_CONFIG.BASE_URL}/api/v1/cbx`;
  private readonly audiobooksUrl = `${API_CONFIG.BASE_URL}/api/v1/audiobooks`;

  constructor() {
    effect(() => {
      if (this.authService.token() === null) {
        this.queryClient.removeQueries({queryKey: readingQueryKeys.all()});
      }
    });
  }

  annotations(bookId: number) {
    return queryOptions({
      queryKey: readingQueryKeys.annotations(bookId),
      queryFn: ({signal}) => httpGet<readonly Annotation[]>(
        this.http,
        `${this.annotationsUrl}/book/${bookId}`,
        signal,
      ),
      ...QUERY_DEFAULTS,
    });
  }

  bookmarks(bookId: number) {
    return queryOptions({
      queryKey: readingQueryKeys.bookmarks(bookId),
      queryFn: ({signal}) => httpGet<readonly Bookmark[]>(
        this.http,
        `${this.bookmarksUrl}/book/${bookId}`,
        signal,
      ),
      ...QUERY_DEFAULTS,
    });
  }

  notes(bookId: number) {
    return queryOptions({
      queryKey: readingQueryKeys.notes(bookId),
      queryFn: ({signal}) => httpGet<readonly Note[]>(
        this.http,
        `${this.notesUrl}/book/${bookId}`,
        signal,
      ),
      ...QUERY_DEFAULTS,
    });
  }

  bookNotes(bookId: number) {
    return queryOptions({
      queryKey: readingQueryKeys.bookNotes(bookId),
      queryFn: ({signal}) => httpGet<readonly BookNote[]>(
        this.http,
        `${this.bookNotesUrl}/book/${bookId}`,
        signal,
      ),
      ...QUERY_DEFAULTS,
    });
  }

  pdfAnnotations(bookId: number) {
    return queryOptions({
      queryKey: readingQueryKeys.pdfAnnotations(bookId),
      queryFn: async ({signal}) => {
        const annotations = await httpGet<{readonly data: string} | null>(
          this.http,
          `${this.pdfAnnotationsUrl}/book/${bookId}`,
          signal,
        );
        return annotations?.data ?? null;
      },
      ...QUERY_DEFAULTS,
    });
  }

  viewerSettings(bookId: number, bookFileId: number) {
    return queryOptions({
      queryKey: readingQueryKeys.viewerSettings(bookId, bookFileId),
      queryFn: ({signal}) => httpGet<ViewerSettings>(
        this.http,
        `${this.booksUrl}/${bookId}/viewer-setting`,
        signal,
        {bookFileId},
      ),
      ...QUERY_DEFAULTS,
    });
  }

  sessions(bookId: number, {page, size}: ReadingSessionPageParams) {
    return queryOptions({
      queryKey: readingQueryKeys.sessions(bookId, {page, size}),
      queryFn: ({signal}) => httpGet<ReadingSessionPage>(
        this.http,
        `${this.readingSessionsUrl}/book/${bookId}`,
        signal,
        {page, size},
      ),
      ...QUERY_DEFAULTS,
    });
  }

  notebookPage({page, size, ...filter}: NotebookPageParams) {
    const normalized = {...normalizeNotebookFilter(filter), page, size};
    return queryOptions({
      queryKey: readingQueryKeys.notebookPage(normalized),
      queryFn: ({signal}) => httpGet<NotebookPage>(
        this.http,
        this.notebookUrl,
        signal,
        {...notebookRequestParams(normalized), page, size},
      ),
      ...QUERY_DEFAULTS,
    });
  }

  notebookExport(filter: NotebookFilterParams) {
    const normalized = normalizeNotebookFilter(filter);
    return queryOptions({
      queryKey: readingQueryKeys.notebookExport(normalized),
      queryFn: ({signal}) => httpGet<readonly NotebookEntry[]>(
        this.http,
        `${this.notebookUrl}/export`,
        signal,
        notebookRequestParams(normalized),
      ),
      ...QUERY_DEFAULTS,
      staleTime: 0,
      gcTime: 0,
    });
  }

  notebookBooks(search: string) {
    const trimmed = search.trim();
    return queryOptions({
      queryKey: readingQueryKeys.notebookBooks(trimmed),
      queryFn: ({signal}) => httpGet<readonly NotebookBook[]>(
        this.http,
        `${this.notebookUrl}/books`,
        signal,
        trimmed ? {search: trimmed} : undefined,
      ),
      ...QUERY_DEFAULTS,
    });
  }

  epubInfo(bookId: number, bookType: BookFileType | null) {
    return queryOptions({
      queryKey: readingQueryKeys.epubInfo(bookId, bookType),
      queryFn: ({signal}) => httpGet<EpubInfo>(
        this.http,
        `${this.epubUrl}/${bookId}/info`,
        signal,
        bookTypeParams(bookType),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  comicPages(bookId: number, bookType: BookFileType | null) {
    return queryOptions({
      queryKey: readingQueryKeys.comicPages(bookId, bookType),
      queryFn: ({signal}) => httpGet<readonly number[]>(
        this.http,
        `${this.cbxUrl}/${bookId}/pages`,
        signal,
        bookTypeParams(bookType),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  comicPageInfo(bookId: number, bookType: BookFileType | null) {
    return queryOptions({
      queryKey: readingQueryKeys.comicPageInfo(bookId, bookType),
      queryFn: ({signal}) => httpGet<readonly ComicPageInfo[]>(
        this.http,
        `${this.cbxUrl}/${bookId}/page-info`,
        signal,
        bookTypeParams(bookType),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  comicPageDimensions(bookId: number, bookType: BookFileType | null) {
    return queryOptions({
      queryKey: readingQueryKeys.comicPageDimensions(bookId, bookType),
      queryFn: ({signal}) => httpGet<readonly ComicPageDimensions[]>(
        this.http,
        `${this.cbxUrl}/${bookId}/page-dimensions`,
        signal,
        bookTypeParams(bookType),
      ),
      ...QUERY_DEFAULTS,
    });
  }

  audiobookInfo(bookId: number, bookType: BookFileType | null) {
    return queryOptions({
      queryKey: readingQueryKeys.audiobookInfo(bookId, bookType),
      queryFn: ({signal}) => httpGet<AudiobookInfo>(
        this.http,
        `${this.audiobooksUrl}/${bookId}/info`,
        signal,
        bookTypeParams(bookType),
      ),
      ...QUERY_DEFAULTS,
    });
  }
}

function normalizeNotebookFilter({types, bookId, search, sort}: NotebookFilterParams): NotebookFilterParams {
  return {
    types: NOTEBOOK_ENTRY_TYPES.filter(type => types.includes(type)),
    bookId,
    search: search.trim(),
    sort,
  };
}

function notebookRequestParams({types, bookId, search, sort}: NotebookFilterParams) {
  return {
    sort,
    types,
    ...(bookId === null ? {} : {bookId}),
    ...(search ? {search} : {}),
  };
}

function bookTypeParams(bookType: BookFileType | null) {
  return bookType === null ? undefined : {bookType};
}
