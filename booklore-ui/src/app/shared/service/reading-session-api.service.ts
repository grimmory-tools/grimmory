import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';
import {BookType} from '../../features/book/model/book.model';

export interface ReadingSessionResponse {
  id: number;
  bookId: number;
  bookTitle: string;
  bookType: BookType;
  startTime: string;
  endTime: string;
  durationSeconds: number;
  startProgress: number;
  endProgress: number;
  progressDelta: number;
  startLocation?: string;
  endLocation?: string;
  createdAt: string;
}

export interface PageableResponse<T> {
  content: T[];
  page: {
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };
}

export interface CreateReadingSessionDto {
  bookId: number;
  bookType: BookType;
  startTime: string;
  endTime: string;
  durationSeconds: number;
  durationFormatted: string;
  startProgress?: number;
  endProgress?: number;
  progressDelta?: number;
  startLocation?: string;
  endLocation?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ReadingSessionApiService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/reading-sessions`;

  createSession(sessionData: CreateReadingSessionDto): Observable<void> {
    return this.http.post<void>(this.url, sessionData);
  }

  getSessionsByBookId(bookId: number, page: number = 0, size: number = 5): Observable<PageableResponse<ReadingSessionResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<PageableResponse<ReadingSessionResponse>>(
      `${this.url}/book/${bookId}`,
      {params}
    );
  }

  sendSessionBeacon(sessionData: CreateReadingSessionDto): boolean {
    try {
      const blob = new Blob([JSON.stringify(sessionData)], {type: 'application/json'});
      return navigator.sendBeacon(this.url, blob);
    } catch {
      return false;
    }
  }
}

