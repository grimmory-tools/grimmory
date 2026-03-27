import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../core/config/api-config';

export interface CoverImage {
  url: string;
  source?: string;
  width?: number;
  height?: number;
  index: number;
}

export interface CoverFetchRequest {
  title?: string;
  author?: string;
  coverType?: 'ebook' | 'audiobook';
}

@Injectable({
  providedIn: 'root'
})
export class BookCoverService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);

  fetchBookCovers(request: CoverFetchRequest): Observable<CoverImage[]> {
    return this.http.post<CoverImage[]>(`${this.baseUrl}/1/metadata/covers`, request);
  }
}

