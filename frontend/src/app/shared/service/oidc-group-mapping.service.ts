import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {OidcGroupMapping} from '../model/oidc-group-mapping.model';
import {API_CONFIG} from '../../core/config/api-config';

@Injectable({providedIn: 'root'})
export class OidcGroupMappingService {

  private http = inject(HttpClient);
  private baseUrl = `${API_CONFIG.BASE_URL}/api/v1/admin/oidc-group-mappings`;

  getAll(): Observable<OidcGroupMapping[]> {
    return this.http.get<OidcGroupMapping[]>(this.baseUrl);
  }

  create(mapping: OidcGroupMapping): Observable<OidcGroupMapping> {
    return this.http.post<OidcGroupMapping>(this.baseUrl, mapping);
  }

  update(id: number, mapping: OidcGroupMapping): Observable<OidcGroupMapping> {
    return this.http.put<OidcGroupMapping>(`${this.baseUrl}/${id}`, mapping);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
