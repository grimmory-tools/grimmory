import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from '../../../core/config/api-config';
import { AudiobookInfo, AudiobookProgress } from './audiobook.model';
import { AuthService } from '../../../shared/service/auth.service';
import { BookFileProgress } from '../../book/model/book.model';

@Injectable({
  providedIn: 'root'
})
export class AudiobookService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/audiobooks`;
  private readonly booksUrl = `${API_CONFIG.BASE_URL}/api/v1/books`;

  private http = inject(HttpClient);
  private authService = inject(AuthService);

  getAudiobookInfo(bookId: number, bookType?: string): Observable<AudiobookInfo> {
    const params: Record<string, string> = {};
    if (bookType) {
      params['bookType'] = bookType;
    }
    return this.http.get<AudiobookInfo>(`${this.baseUrl}/${bookId}/info`, { params });
  }

  getStreamUrl(bookId: number, trackIndex?: number): string {
    const token = this.authService.getInternalAccessToken();
    let url = `${this.baseUrl}/${bookId}/stream?ngsw-bypass=true&token=${encodeURIComponent(token || '')}`;
    if (trackIndex !== undefined && trackIndex !== null) {
      url += `&trackIndex=${trackIndex}`;
    }
    return url;
  }

  getTrackStreamUrl(bookId: number, trackIndex: number): string {
    const token = this.authService.getInternalAccessToken();
    return `${this.baseUrl}/${bookId}/track/${trackIndex}/stream?ngsw-bypass=true&token=${encodeURIComponent(token || '')}`;
  }

  getEmbeddedCoverUrl(bookId: number): string {
    const token = this.authService.getInternalAccessToken();
    return `${this.baseUrl}/${bookId}/cover?ngsw-bypass=true&token=${encodeURIComponent(token || '')}`;
  }

  saveProgress(bookId: number, progress: AudiobookProgress, bookFileId?: number): Observable<void> {
    if (bookFileId) {
      const fileProgress: BookFileProgress = {
        bookFileId: bookFileId,
        positionData: progress.positionMs.toString(),
        positionHref: progress.trackIndex?.toString(),
        progressPercent: progress.percentage
      };
      return this.http.post<void>(`${this.booksUrl}/progress`, {
        bookId,
        fileProgress
      });
    }
    return this.http.post<void>(`${this.booksUrl}/progress`, {
      bookId,
      audiobookProgress: progress
    });
  }
}
