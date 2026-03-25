import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../core/config/api-config';
import {ContentRestriction} from './content-restriction.model';

@Injectable({
  providedIn: 'root'
})
export class ContentRestrictionService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/users`;
  private http = inject(HttpClient);

  getUserRestrictions(userId: number): Observable<ContentRestriction[]> {
    return this.http.get<ContentRestriction[]>(`${this.baseUrl}/${userId}/content-restrictions`);
  }

  addRestriction(userId: number, restriction: ContentRestriction): Observable<ContentRestriction> {
    return this.http.post<ContentRestriction>(`${this.baseUrl}/${userId}/content-restrictions`, restriction);
  }

  updateRestrictions(userId: number, restrictions: ContentRestriction[]): Observable<ContentRestriction[]> {
    return this.http.put<ContentRestriction[]>(`${this.baseUrl}/${userId}/content-restrictions`, restrictions);
  }

  deleteRestriction(userId: number, restrictionId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${userId}/content-restrictions/${restrictionId}`);
  }

  deleteAllRestrictions(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${userId}/content-restrictions`);
  }
}
