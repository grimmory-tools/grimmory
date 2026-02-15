import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable, Subject} from 'rxjs';
import {distinctUntilChanged, exhaustMap, share, tap} from 'rxjs/operators';
import {Book, BookFileProgress, ReadStatus} from '../model/book.model';
import {BookStateService} from './book-state.service';
import {API_CONFIG} from '../../../core/config/api-config';
import {ResetProgressType, ResetProgressTypes} from '../../../shared/constants/reset-progress-type';
import {BookStatusUpdateResponse, PersonalRatingUpdateResponse} from '../model/book.model';

@Injectable({
  providedIn: 'root',
})
export class BookPatchService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private bookStateService = inject(BookStateService);

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
      const body: any = {
        bookId: payload.bookId,
        epubProgress: {
          cfi: payload.cfi,
          href: payload.href,
          percentage: payload.percentage
        }
      };
      // Add file-level progress if bookFileId is provided
      if (payload.bookFileId) {
        body.fileProgress = {
          bookFileId: payload.bookFileId,
          positionData: payload.cfi,
          positionHref: payload.href,
          progressPercent: payload.percentage
        };
      }
      return this.http.post<void>(`${this.url}/progress`, body);
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
        const currentState = this.bookStateService.getCurrentBookState();
        const currentBooks = currentState.books || [];
        updatedBooks.forEach(updatedBook => {
          const index = currentBooks.findIndex(b => b.id === updatedBook.id);
          if (index !== -1) {
            currentBooks[index] = updatedBook;
          }
        });
        this.bookStateService.updateBookState({...currentState, books: [...currentBooks]});
      })
    );
  }

  savePdfProgress(bookId: number, page: number, percentage: number, bookFileId?: number): Observable<void> {
    const body: any = {
      bookId: bookId,
      pdfProgress: {
        page: page,
        percentage: percentage
      }
    };
    // Add file-level progress if bookFileId is provided
    if (bookFileId) {
      body.fileProgress = {
        bookFileId: bookFileId,
        positionData: String(page),
        progressPercent: percentage
      };
    }
    return this.http.post<void>(`${this.url}/progress`, body);
  }

  saveEpubProgress(bookId: number, cfi: string, href: string, percentage: number, bookFileId?: number): void {
    this.epubProgressSubject.next({bookId, cfi, href, percentage, bookFileId});
  }

  saveCbxProgress(bookId: number, page: number, percentage: number, bookFileId?: number): Observable<void> {
    const body: any = {
      bookId: bookId,
      cbxProgress: {
        page: page,
        percentage: percentage
      }
    };
    // Add file-level progress if bookFileId is provided
    if (bookFileId) {
      body.fileProgress = {
        bookFileId: bookFileId,
        positionData: String(page),
        progressPercent: percentage
      };
    }
    return this.http.post<void>(`${this.url}/progress`, body);
  }

  /**
   * Save file-level progress directly using the new API.
   * This is the preferred method for per-file progress tracking.
   */
  saveFileProgress(bookId: number, fileProgress: BookFileProgress): Observable<void> {
    const body = {
      bookId: bookId,
      fileProgress: fileProgress
    };
    return this.http.post<void>(`${this.url}/progress`, body);
  }

  updateDateFinished(bookId: number, dateFinished: string | null): Observable<void> {
    const body = {
      bookId: bookId,
      dateFinished: dateFinished
    };
    return this.http.post<void>(`${this.url}/progress`, body).pipe(
      tap(() => {
        const currentState = this.bookStateService.getCurrentBookState();
        if (currentState.books) {
          const updatedBooks = currentState.books.map(book => {
            if (book.id === bookId) {
              return {...book, dateFinished: dateFinished || undefined};
            }
            return book;
          });
          this.bookStateService.updateBookState({
            ...currentState,
            books: updatedBooks
          });
        }
      })
    );
  }

  resetProgress(bookIds: number | number[], type: ResetProgressType): Observable<BookStatusUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];
    const params = new HttpParams().set('type', ResetProgressTypes[type]);

    return this.http.post<BookStatusUpdateResponse[]>(`${this.url}/reset-progress`, ids, {params}).pipe(
      tap(responses => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          const response = responses.find(r => r.bookId === book.id);
          if (response) {
            const progressReset: Partial<Book> =
              type === 'KOREADER' ? {koreaderProgress: undefined} :
              type === 'KOBO' ? {koboProgress: undefined} :
              {epubProgress: undefined, pdfProgress: undefined, cbxProgress: undefined, audiobookProgress: undefined};
            return {
              ...book,
              ...progressReset,
              readStatus: response.readStatus,
              readStatusModifiedTime: response.readStatusModifiedTime,
              dateFinished: response.dateFinished
            };
          }
          return book;
        });
        this.bookStateService.updateBookState({...currentState, books: updatedBooks});
      })
    );
  }

  updateBookReadStatus(bookIds: number | number[], status: ReadStatus): Observable<BookStatusUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];
    const requestBody = {
      bookIds: ids,
      status: status
    };

    return this.http.post<BookStatusUpdateResponse[]>(`${this.url}/status`, requestBody).pipe(
      tap(responses => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          const response = responses.find(r => r.bookId === book.id);
          if (response) {
            return {
              ...book,
              readStatus: response.readStatus,
              readStatusModifiedTime: response.readStatusModifiedTime,
              dateFinished: response.dateFinished
            };
          }
          return book;
        });
        this.bookStateService.updateBookState({...currentState, books: updatedBooks});
      })
    );
  }

  resetPersonalRating(bookIds: number | number[]): Observable<PersonalRatingUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];

    return this.http.post<PersonalRatingUpdateResponse[]>(`${this.url}/reset-personal-rating`, ids).pipe(
      tap(responses => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          const response = responses.find(r => r.bookId === book.id);
          if (response) {
            return {
              ...book,
              personalRating: response.personalRating
            };
          }
          return book;
        });
        this.bookStateService.updateBookState({...currentState, books: updatedBooks});
      })
    );
  }

  updatePersonalRating(bookIds: number | number[], rating: number): Observable<PersonalRatingUpdateResponse[]> {
    const ids = Array.isArray(bookIds) ? bookIds : [bookIds];

    return this.http.put<PersonalRatingUpdateResponse[]>(`${this.url}/personal-rating`, {ids, rating}).pipe(
      tap(responses => {
        const currentState = this.bookStateService.getCurrentBookState();
        const updatedBooks = (currentState.books || []).map(book => {
          const response = responses.find(r => r.bookId === book.id);
          if (response) {
            return {
              ...book,
              personalRating: response.personalRating
            };
          }
          return book;
        });
        this.bookStateService.updateBookState({...currentState, books: updatedBooks});
      })
    );
  }

  updateLastReadTime(bookId: number): void {
    const timestamp = new Date().toISOString();
    const currentState = this.bookStateService.getCurrentBookState();
    const updatedBooks = (currentState.books || []).map(book =>
      book.id === bookId ? {...book, lastReadTime: timestamp} : book
    );
    this.bookStateService.updateBookState({...currentState, books: updatedBooks});
  }
}
