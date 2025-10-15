import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EmailV2Service {

  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v2/emails`;

  private http = inject(HttpClient);

  emailBook(request: { bookId: number, providerId: number, recipientId: number }): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/send-book`, request);
  }

  emailBookQuick(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/send-book/${bookId}`, {});
  }
}
