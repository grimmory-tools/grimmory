import {inject, Service} from '@angular/core';
import {Observable} from 'rxjs';
import {HttpClient, HttpParams} from '@angular/common/http';
import {API_CONFIG} from '../../../core/config/api-config';

@Service()
export class UtilityService {
  private readonly http = inject(HttpClient);

  private readonly pathUrl = `${API_CONFIG.BASE_URL}/api/v1/path`;

  getFolders(path: string): Observable<string[]> {
    const params = new HttpParams().set('path', path);
    return this.http.get<string[]>(this.pathUrl, {params});
  }
}
