import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {BookdropFileNotification} from './bookdrop-file.service';

@Injectable({
  providedIn: 'root'
})
export class BookdropFileApiService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/bookdrop`;
  private http = inject(HttpClient);

  getNotification(): Observable<BookdropFileNotification> {
    return this.http.get<BookdropFileNotification>(`${this.url}/notification`);
  }
}
