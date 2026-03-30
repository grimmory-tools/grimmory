import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

export interface SetupPayload {
  email: string;
  password: string;
}

@Injectable({providedIn: 'root'})
export class SetupService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/setup`;

  private http = inject(HttpClient);

  createAdmin(payload: SetupPayload): Observable<void> {
    return this.http.post<void>(this.url, payload);
  }
}
