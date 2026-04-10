import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable, Subject} from 'rxjs';
import {distinctUntilChanged, exhaustMap, share, tap} from 'rxjs/operators';
import {Book, BookFileProgress, ReadStatus} from '../model/book.model';
import {API_CONFIG} from '../../../core/config/api-config';
import {ResetProgressType, ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {BookStatusUpdateResponse, PersonalRatingUpdateResponse} from '../model/book.model';
import {QueryClient} from '@tanstack/angular-query-experimental';
import {BOOKS_QUERY_KEY} from './book-query-keys';
import {
  invalidateAppBooksQueries,
  patchBooksInCache,
  patchBookFieldsInCache,
} from './book-query-cache';

function getResetProgressFields(type: ResetProgressType): Partial<Book> {
  if (type === ResetProgressTypes.KOREADER) {
    return {koreaderProgress: undefined};
  }

  if (type === ResetProgressTypes.KOBO) {
    return {koboProgress: undefined};
  }

  return {
    epubProgress: undefined,
    pdfProgress: undefined,
    cbxProgress: undefined,
    audiobookProgress: undefined,
  };
}

@Injectable({
  providedIn: 'root',
})
export class BookPatchService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private queryClient = inject(QueryClient);

  private epubProgressSubject = new Subject<{ bookId: number; cfi: string; href: string; percentage: number; bookFileId?: number }>();

  private epubProgress$ = this.epubProgressSubject.pipe(
    distinctUntilChanged((prev, curr) =>
      prev.bookId === curr.bookId &&
      prev.cfi === curr.cfi &&
      prev.href === curr.href &&
      prev.percentage === curr.percentage &&
      prev.bookFileId === curr.bookFileId
    ),
    exhaustMap(payload => {
      const body: {
        bookId: number;
        epubProgress: {
          cfi: string;
          href: string;
          percentage: number;
        };
        fileProgress?: {
          bookFileId: number;
          positionData: string;
          positionHref: string;
          progressPercent: number;
        };
      } = {
        bookId: payload.bookId,
        epubProgress: {
          cfi: payload.cfi,
          href: payload.href,
          percentage: payload.percentage
        }
      };
      if (payload.bookFileId) {
        body.fileProgress = {
          bookFileId: payload.bookFileId,
          positionData: payload.cfi,
          positionHref: payload.href,
          progressPercent: payload.percentage
        };
      }
      return this.http.post<void>(`${this.url}/progress`, body).pipe(
        tap(() => {
          patchBookFieldsInCache(this.queryClient, [{
            bookId: payload.bookId,
            fields: {epubProgress: {cfi: payload.cfi, percentage: payload.percentage}}
          }]);
        })
      );
    }),
    share()
  );

  constructor() {
    this.epubProgress$.subscribe();
  }

  updateBookShelves(bookIds: Set<number | undefined>, shelvesToAssign: Set<number | null | undefined>, shelvesToUnassign: Set<number | null | undefined>): Observable<Book[]> {
    const requestPayload = {
      bookIds: Array.from(bookIds),
      shelvesToAssign: Array.from(shelvesToAssign),
      shelvesToUnassign: Array.from(shelvesToUnassign),
    };
    return this.http.post<Book[]>(`${this.url}/shelves`, requestPayload).pipe(
      tap(updatedBooks => {
        patchBooksInCache(this.queryClient, updatedBooks);
      })
    );
  }

  savePdfProgress(bookId: number, page: number, percentage: number, bookFileId?: number): Observable<void> {
    const body: {
      bookId: number;
      pdfProgress: {
        page: number;
        percentage: number;
      };
      fileProgress?: {
        bookFileId: number;
        positionData: string;
        progressPercent: number;
      };
    } = {
      bookId,
      pdfProgress: {
        page,
        percentage
      }
    };
    if (bookFileId) {
      body.fileProgress = {
        bookFileId,
        positionData: String(page),
        progressPercent: percentage
      };
    }
    return this.http.post<void>(`${this.url}/progress`, body).pipe(
      tap(() => {
        patchBookFieldsInCache(this.queryClient, [{
          bookId,
          fields: {pdfProgress: {page, percentage}}
        }]);
      })
    );
  }

  saveEpubProgress(bookId: number, cfi: string, href: string, percentage: number, bookFileId?: number): void {
    this.epubProgressSubject.next({bookId, cfi, href, percentage, bookFileId});
  }

  saveCbxProgress(bookId: number, page: number, percentage: number, bookFileId?: number): Observable<void> {
    const body: {
      bookId: number;
      cbxProgress: {
        page: number;
        percentage: number;
      };
      fileProgress?: {
        bookFileId: number;
        positionData: string;
        progressPercent: number;
      };
    } = {
      bookId,
      cbxProgress: {
        page,
        percentage
      }
    };
    if (bookFileId) {
      body.fileProgress = {
        bookFileId,
        positionData: String(page),
        progressPercent: percentage
      };
    }
    return this.http.post<void>(`${this.url}/progress`, body).pipe(
      tap(() => {
        patchBookFieldsInCache(this.queryClient, [{
          bookId,
          fields: {cbxProgress: {page, percentage}}
        }]);
      })
    );
  }

  saveFileProgress(bookId: number, fileProgress: BookFileProgress): Observable<void> {
    return this.http.post<void>(`${this.url}/progress`, {
      bookId,
      fileProgress
    });
  }

  updateDateFinished(bookId: number, dateFinished: string | null): Observable<void> {
    return this.http.post<void>(`${this.url}/progress`, {
      bookId,
      dateFinished
    }).pipe(
      tap(() => {
        patchBookFieldsInCache(this.queryClient, [{bookId, fields: {dateFinished: dateFinished || undefined}}]);
      })
    );
  }

  resetProgress(bookIds: number | number[], type: ResetProgressType): Observable<BookStatusUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];
    const params = new HttpParams().set('type', type);

    return this.http.post<BookStatusUpdateResponse[]>(`${this.url}/reset-progress`, ids, {params}).pipe(
      tap(responses => {
        patchBookFieldsInCache(this.queryClient, responses.map(r => ({
          bookId: r.bookId,
          fields: {
            ...getResetProgressFields(type),
            readStatus: r.readStatus,
            readStatusModifiedTime: r.readStatusModifiedTime,
            dateFinished: r.dateFinished
          }
        })));
      })
    );
  }

  updateBookReadStatus(bookIds: number | number[], status: ReadStatus): Observable<BookStatusUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];

    return this.http.post<BookStatusUpdateResponse[]>(`${this.url}/status`, {
      bookIds: ids,
      status
    }).pipe(
      tap(responses => {
        patchBookFieldsInCache(this.queryClient, responses.map(r => ({
          bookId: r.bookId,
          fields: {readStatus: r.readStatus, readStatusModifiedTime: r.readStatusModifiedTime, dateFinished: r.dateFinished}
        })));
      })
    );
  }

  resetPersonalRating(bookIds: number | number[]): Observable<PersonalRatingUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];

    return this.http.post<PersonalRatingUpdateResponse[]>(`${this.url}/reset-personal-rating`, ids).pipe(
      tap(responses => {
        patchBookFieldsInCache(this.queryClient, responses.map(r => ({
          bookId: r.bookId,
          fields: {personalRating: r.personalRating}
        })));
      })
    );
  }

  updatePersonalRating(bookIds: number | number[], rating: number): Observable<PersonalRatingUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];

    return this.http.put<PersonalRatingUpdateResponse[]>(`${this.url}/personal-rating`, {ids, rating}).pipe(
      tap(responses => {
        patchBookFieldsInCache(this.queryClient, responses.map(r => ({
          bookId: r.bookId,
          fields: {personalRating: r.personalRating}
        })));
      })
    );
  }

  updateLastReadTime(bookId: number): void {
    const timestamp = new Date().toISOString();
    this.queryClient.setQueryData<Book[]>(BOOKS_QUERY_KEY, current =>
      (current ?? []).map(book =>
        book.id === bookId ? {...book, lastReadTime: timestamp} : book
      )
    );
    invalidateAppBooksQueries(this.queryClient);
  }
}
