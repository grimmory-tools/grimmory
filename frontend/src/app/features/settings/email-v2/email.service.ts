import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EmailService {

  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/email`;

  private http = inject(HttpClient);

  emailBook(request: { bookId: number, providerId: number, recipientId: number, bookFileId?: number }): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/book`, request);
  }

  emailBookQuick(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/book/${bookId}`, {});
  }
}
