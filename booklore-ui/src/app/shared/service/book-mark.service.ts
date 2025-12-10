import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export interface BookMark {
  id: number;
  bookId: number;
  cfi: string;
  title: string;
  createdAt: string;
}

export interface CreateBookMarkRequest {
  bookId: number;
  cfi: string;
  title?: string;
}

@Injectable({
  providedIn: 'root'
})
export class BookMarkService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/bookmarks`;
  private readonly http = inject(HttpClient);

  getBookmarksForBook(bookId: number): Observable<BookMark[]> {
    return this.http.get<BookMark[]>(`${this.url}/book/${bookId}`);
  }

  createBookmark(request: CreateBookMarkRequest): Observable<BookMark> {
    return this.http.post<BookMark>(this.url, request);
  }

  deleteBookmark(bookmarkId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${bookmarkId}`);
  }
}
