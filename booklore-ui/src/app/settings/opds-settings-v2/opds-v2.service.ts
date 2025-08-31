import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../config/api-config';

export interface OpdsUserV2CreateRequest {
  username: string;
  password: string;
}

export interface OpdsUserV2 {
  id: number;
  userId: number;
  username: string;
}

@Injectable({
  providedIn: 'root'
})
export class OpdsV2Service {

  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v2/opds-users`;
  private http = inject(HttpClient);

  getUser(): Observable<OpdsUserV2[]> {
    return this.http.get<OpdsUserV2[]>(this.baseUrl);
  }

  createUser(user: OpdsUserV2CreateRequest): Observable<OpdsUserV2> {
    return this.http.post<OpdsUserV2>(this.baseUrl, user);
  }

  deleteCredential(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
