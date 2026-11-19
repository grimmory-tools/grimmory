import {inject, Service} from '@angular/core';
import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from './auth.service';

@Service()
export class UrlHelperService {
  private readonly authService = inject(AuthService);
  private readonly baseUrl = API_CONFIG.BASE_URL;
  private readonly mediaBaseUrl = `${this.baseUrl}/api/v1/media`;

  private getToken(): string | null {
    return this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getThumbnailUrl(bookId: number, coverUpdatedOn?: string): string | null {
    if (!coverUpdatedOn) return null;
    return this.appendToken(`${this.mediaBaseUrl}/book/${bookId}/thumbnail?${coverUpdatedOn}`);
  }

  getDirectThumbnailUrl(bookId: number, coverUpdatedOn?: string): string {
    let url = `${this.mediaBaseUrl}/book/${bookId}/thumbnail`;
    if (coverUpdatedOn) {
      url += `?${coverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getCoverUrl(bookId: number, coverUpdatedOn?: string): string | null {
    if (!coverUpdatedOn) return null;
    return this.appendToken(`${this.mediaBaseUrl}/book/${bookId}/cover?${coverUpdatedOn}`);
  }

  getBackupCoverUrl(bookId: number): string {
    const url = `${this.mediaBaseUrl}/book/${bookId}/backup-cover`;
    return this.appendToken(url);
  }

  getAudiobookCoverUrl(bookId: number, audiobookCoverUpdatedOn?: string): string | null {
    if (!audiobookCoverUpdatedOn) return null;
    return this.appendToken(`${this.mediaBaseUrl}/book/${bookId}/audiobook-cover?${audiobookCoverUpdatedOn}`);
  }

  getAudiobookThumbnailUrl(bookId: number, audiobookCoverUpdatedOn?: string): string | null {
    if (!audiobookCoverUpdatedOn) return null;
    return this.appendToken(`${this.mediaBaseUrl}/book/${bookId}/audiobook-thumbnail?${audiobookCoverUpdatedOn}`);
  }

  getDirectAudiobookThumbnailUrl(bookId: number, audiobookCoverUpdatedOn?: string): string {
    let url = `${this.mediaBaseUrl}/book/${bookId}/audiobook-thumbnail`;
    if (audiobookCoverUpdatedOn) {
      url += `?${audiobookCoverUpdatedOn}`;
    }
    return this.appendToken(url);
  }

  getBookdropCoverUrl(bookdropId: number): string {
    const url = `${this.mediaBaseUrl}/bookdrop/${bookdropId}/cover`;
    return this.appendToken(url);
  }
}
