import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export interface BookNoteV2 {
  id: number;
  userId?: number;
  bookId: number;
  cfi: string;
  selectedText?: string;
  noteContent: string;
  color?: string;
  chapterTitle?: string;
  createdAt: string;
  updatedAt?: string;
}

export interface CreateBookNoteV2Request {
  bookId: number;
  cfi: string;
  selectedText?: string;
  noteContent: string;
  color?: string;
  chapterTitle?: string;
}

export interface UpdateBookNoteV2Request {
  noteContent?: string;
  color?: string;
  chapterTitle?: string;
}

@Injectable({
  providedIn: 'root'
})
export class BookNoteV2Service {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v2/book-notes`;
  private readonly http = inject(HttpClient);

  getNotesForBook(bookId: number): Observable<BookNoteV2[]> {
    return this.http.get<BookNoteV2[]>(`${this.url}/book/${bookId}`);
  }

  createNote(request: CreateBookNoteV2Request): Observable<BookNoteV2> {
    return this.http.post<BookNoteV2>(this.url, request);
  }

  deleteNote(noteId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${noteId}`);
  }

  updateNote(noteId: number, request: UpdateBookNoteV2Request): Observable<BookNoteV2> {
    return this.http.put<BookNoteV2>(`${this.url}/${noteId}`, request);
  }
}
