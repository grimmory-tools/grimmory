import {Service, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export interface BookNote {
  id: number;
  userId: number;
  bookId: number;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBookNoteRequest {
  id?: number;
  bookId: number;
  title: string;
  content: string;
}

@Service()
export class BookNoteService {
  private readonly http = inject(HttpClient);

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/book-notes`;

  getNotesForBook(bookId: number): Observable<BookNote[]> {
    return this.http.get<BookNote[]>(`${this.url}/book/${bookId}`);
  }

  createOrUpdateNote(request: CreateBookNoteRequest): Observable<BookNote> {
    return this.http.post<BookNote>(this.url, request);
  }

  deleteNote(noteId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${noteId}`);
  }
}
