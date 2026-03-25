import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from '../../core/config/api-config';

@Injectable({ providedIn: 'root' })
export class MetadataMatchWeightsService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1`;

  private http = inject(HttpClient);

  recalculateAll(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/books/metadata/recalculate-match-scores`, {});
  }
}
