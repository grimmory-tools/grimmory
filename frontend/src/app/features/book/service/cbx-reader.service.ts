import {HttpClient} from '@angular/common/http';
import {Injectable, inject} from '@angular/core';
import {API_CONFIG} from '../../../core/config/api-config';
import {AuthService} from '../../../shared/service/auth.service';

export interface CbxPageInfo {
  pageNumber: number;
  displayName: string;
}

@Injectable({providedIn: 'root'})
export class CbxReaderService {

  private readonly pagesUrl = `${API_CONFIG.BASE_URL}/api/v1/cbx`;
  private readonly imageUrl = `${API_CONFIG.BASE_URL}/api/v1/media/book`;
  private authService = inject(AuthService);
  private http = inject(HttpClient);

  private getToken(): string | null {
    return this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getAvailablePages(bookId: number, bookType?: string) {
    let url = `${this.pagesUrl}/${bookId}/pages`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.http.get<number[]>(this.appendToken(url));
  }

  getPageInfo(bookId: number, bookType?: string) {
    let url = `${this.pagesUrl}/${bookId}/page-info`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.http.get<CbxPageInfo[]>(this.appendToken(url));
  }

  getPageImageUrl(bookId: number, page: number, bookType?: string): string {
    let url = `${this.imageUrl}/${bookId}/cbx/pages/${page}`;
    if (bookType) {
      url += `?bookType=${bookType}`;
    }
    return this.appendToken(url);
  }
}
