import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../config/api-config';
import {AuthService} from '../../core/service/auth.service';

@Injectable({
  providedIn: 'root'
})
export class UrlHelperService {
  private readonly baseUrl = API_CONFIG.BASE_URL;
  private readonly mediaBaseUrl = `${this.baseUrl}/api/v1/media`;
  private authService = inject(AuthService);

  private getToken(): string | null {
    return this.authService.getOidcAccessToken() || this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getThumbnailUrl(bookId: number, coverUpdatedOn?: string): string {
    if (!coverUpdatedOn) return 'assets/images/missing-cover.jpg';
    const url = `${this.mediaBaseUrl}/book/${bookId}/thumbnail?${coverUpdatedOn}`;
    return this.appendToken(url);
  }

  getCoverUrl(bookId: number, coverUpdatedOn?: string): string {
    if (!coverUpdatedOn) return 'assets/images/missing-cover.jpg';
    const url = `${this.mediaBaseUrl}/book/${bookId}/cover?${coverUpdatedOn}`;
    return this.appendToken(url);
  }

  getBackupCoverUrl(bookId: number): string {
    const url = `${this.mediaBaseUrl}/book/${bookId}/backup-cover`;
    return this.appendToken(url);
  }

  getBookdropCoverUrl(bookdropId: number): string {
    const url = `${this.mediaBaseUrl}/bookdrop/${bookdropId}/cover`;
    return this.appendToken(url);
  }
}
