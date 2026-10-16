import {inject, Service} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

@Service()
export class EmailService {
  private readonly http = inject(HttpClient);

  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/email`;

  emailBook(request: { bookId: number, providerId: number, recipientId: number, bookFileId?: number }): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/book`, request);
  }

  emailBookQuick(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/book/${bookId}`, {});
  }
}
