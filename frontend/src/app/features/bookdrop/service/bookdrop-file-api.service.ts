import {inject, Service} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {BookdropFileNotification} from './bookdrop-file.service';

@Service()
export class BookdropFileApiService {
  private readonly http = inject(HttpClient);

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/bookdrop`;

  getNotification(): Observable<BookdropFileNotification> {
    return this.http.get<BookdropFileNotification>(`${this.url}/notification`);
  }
}
