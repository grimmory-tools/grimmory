import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from './config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

export interface OpdsUser {
  id: number;
  username: string;
  password: string;
}

@Injectable({
  providedIn: 'root'
})
export class OpdsUserService {

  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/opds-users`;

  private http = inject(HttpClient);

  constructor() {
  }

  createUser(userData: Omit<OpdsUser, 'id'>): Observable<void> {
    return this.http.post<void>(this.url, userData);
  }

  getUsers(): Observable<OpdsUser[]> {
    return this.http.get<OpdsUser[]>(this.url);
  }

  deleteUser(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${userId}`);
  }

  resetPassword(resetPasswordUserId: number, newPassword: string): Observable<void> {
    const resetPayload = {
      password: newPassword
    };
    return this.http.put<void>(`${this.url}/${resetPasswordUserId}/reset-password`, resetPayload);
  }
}
