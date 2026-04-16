import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from '../service/auth.service';

export interface CoverImage {
  url: string;
  source?: string;
  width?: number;
  height?: number;
  index: number;
}

export interface CoverFetchRequest {
  bookId?: number;
  title?: string;
  author?: string;
  coverType?: 'ebook' | 'audiobook';
}

@Injectable({
  providedIn: 'root'
})
export class BookCoverService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private authService = inject(AuthService);

  fetchBookCovers(request: CoverFetchRequest): Observable<CoverImage> {
    const token = this.authService.getInternalAccessToken();

    return new Observable<CoverImage>(subscriber => {
      if (!request.bookId) {
        subscriber.error(new Error('bookId is required for fetching covers'));
        return;
      }

      const abortController = new AbortController();

      fetch(`${this.baseUrl}/${request.bookId}/metadata/covers`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(request),
        signal: abortController.signal
      })
        .then(async response => {
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
              const image = JSON.parse(data) as CoverImage;
              subscriber.next(image);
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
        .catch(error => {
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
}

