import {inject, Service} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export interface FileMoveRequest {
  bookIds: number[];
  moves: {
    bookId: number;
    targetLibraryId: number | null;
    targetLibraryPathId: number | null;
  }[];
}

@Service()
export class FileOperationsService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/files`;

  private readonly http = inject(HttpClient);

  moveFiles(request: FileMoveRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/move`, request);
  }
}
