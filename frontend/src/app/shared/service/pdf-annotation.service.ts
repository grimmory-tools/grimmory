import {Service, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

@Service()
export class PdfAnnotationService {
  private readonly http = inject(HttpClient);

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/pdf-annotations`;

  getAnnotations(bookId: number): Observable<{ data: string }> {
    return this.http.get<{ data: string }>(`${this.url}/book/${bookId}`);
  }

  saveAnnotations(bookId: number, data: string): Observable<void> {
    return this.http.put<void>(`${this.url}/book/${bookId}`, {data});
  }

  deleteAnnotations(bookId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/book/${bookId}`);
  }
}
