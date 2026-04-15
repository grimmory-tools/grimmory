import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {FetchMetadataRequest} from '../../metadata/model/request/fetch-metadata-request.model';
import {BookMetadata} from '../model/book.model';
import {AuthService} from '../../../shared/service/auth.service';
import {HttpClient} from '@angular/common/http';

@Injectable({providedIn: 'root'})
export class BookMetadataService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/books`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  fetchBookMetadata(bookId: number, request: FetchMetadataRequest): Observable<BookMetadata> {
    const token = this.authService.getInternalAccessToken();

    if (!token) {
      throw new Error('No authentication token available');
    }

    return new Observable<BookMetadata>((subscriber) => {
      const abortController = new AbortController();

      fetch(`${this.url}/${bookId}/metadata/prospective`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(request),
        signal: abortController.signal,
      })
        .then(async (response) => {
          if (!response.ok) {
            subscriber.error(new Error(`HTTP error! status: ${response.status}`));
            return;
          }

          const reader = response.body?.getReader();
          if (!reader) {
            subscriber.error(new Error('Response body is null'));
            return;
          }

          const decoder = new TextDecoder();
          let buffer = '';
          const emitLine = (line: string) => {
            if (!line.startsWith('data:')) {
              return;
            }

            const data = line.slice(5).trim();
            if (!data) {
              return;
            }

            try {
              const metadata = JSON.parse(data) as BookMetadata;
              subscriber.next(metadata);
            } catch (e) {
              console.error('Error parsing SSE data:', e);
            }
          };

          try {
            while (true) {
              const {done, value} = await reader.read();
              if (done) break;

              buffer += decoder.decode(value, {stream: true});
              const lines = buffer.split('\n');
              buffer = lines.pop() || '';

              for (const line of lines) {
                emitLine(line);
              }
            }
            if (buffer) {
              emitLine(buffer);
            }
            subscriber.complete();
          } catch (error) {
            if (error instanceof Error && error.name === 'AbortError') {
              // Silently handle abort
            } else {
              subscriber.error(error);
            }
          } finally {
            reader.releaseLock();
          }
        })
        .catch((error) => {
          if (error instanceof Error && error.name === 'AbortError') {
            return;
          }
          subscriber.error(error);
        });

      return () => {
        abortController.abort();
      };
    });
  }

  fetchMetadataDetail(provider: string, providerItemId: string): Observable<BookMetadata> {
    return this.http.get<BookMetadata>(`${this.url}/metadata/detail/${provider}/${providerItemId}`);
  }

  lookupByIsbn(isbn: string): Observable<BookMetadata> {
    return this.http.post<BookMetadata>(`${this.url}/metadata/isbn-lookup`, {isbn});
  }
}
