import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';

export interface SetupPayload {
  email: string;
  password: string;
}

@Service()
export class SetupService {
  private readonly http = inject(HttpClient);

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/setup`;

  createAdmin(payload: SetupPayload): Observable<void> {
    return this.http.post<void>(this.url, payload);
  }
}
