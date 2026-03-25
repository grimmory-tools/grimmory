import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

@Injectable({
  providedIn: 'root'
})
export class PdfAnnotationService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/pdf-annotations`;
  private readonly http = inject(HttpClient);

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
