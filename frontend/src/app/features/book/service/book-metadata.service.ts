import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {FetchMetadataRequest} from '../../metadata/model/request/fetch-metadata-request.model';
import {BookMetadata} from '../model/book.model';
import {AuthService} from '../../../shared/service/auth.service';
import {SseClient} from 'ngx-sse-client';
import {HttpClient, HttpHeaders} from '@angular/common/http';
import {map} from 'rxjs/operators';

@Injectable({providedIn: 'root'})
export class BookMetadataService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private sseClient = inject(SseClient);

  fetchBookMetadata(bookId: number, request: FetchMetadataRequest): Observable<BookMetadata> {
    const token = this.authService.getInternalAccessToken();

    if (!token) {
      throw new Error('No authentication token available');
    }

    const headers = new HttpHeaders()
      .set('Content-Type', 'application/json')
      .set('Authorization', `Bearer ${token}`);

    return this.sseClient.stream(
      `${this.url}/${bookId}/metadata/prospective`,
      {
        keepAlive: false,
        reconnectionDelay: 1000,
        responseType: 'event'
      },
      {
        headers,
        body: request,
        withCredentials: true
      },
      'POST'
    ).pipe(
      map((event) => {
        if (event.type === 'error') {
          const errorEvent = event as ErrorEvent;
          throw new Error(errorEvent.message);
        } else {
          const messageEvent = event as MessageEvent;
          return JSON.parse(messageEvent.data) as BookMetadata;
        }
      })
    );
  }

  fetchMetadataDetail(provider: string, providerItemId: string): Observable<BookMetadata> {
    return this.http.get<BookMetadata>(`${this.url}/metadata/detail/${provider}/${providerItemId}`);
  }

  lookupByIsbn(isbn: string): Observable<BookMetadata> {
    return this.http.post<BookMetadata>(`${this.url}/metadata/isbn-lookup`, {isbn});
  }
}
