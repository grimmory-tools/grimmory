import {Injectable, inject} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {API_CONFIG} from '../../../../core/config/api-config';
import {AuthService} from '../../../../shared/service/auth.service';

export interface EpubSpineItem {
  idref: string;
  href: string;
  mediaType: string;
  linear: boolean;
}

export interface EpubManifestItem {
  id: string;
  href: string;
  mediaType: string;
  properties?: string[];
  size: number;
}

export interface EpubTocItem {
  label: string;
  href: string;
  children?: EpubTocItem[];
}

export interface EpubBookInfo {
  containerPath: string;
  rootPath: string;
  spine: EpubSpineItem[];
  manifest: EpubManifestItem[];
  toc: EpubTocItem;
  metadata: Record<string, any>;
  coverPath?: string;
}

@Injectable({
  providedIn: 'root'
})
export class EpubStreamingService {
  private readonly baseUrl = `${API_CONFIG.BASE_URL}/api/v1/epub`;
  private http = inject(HttpClient);
  private authService = inject(AuthService);

  getBookInfo(bookId: number, bookType?: string): Observable<EpubBookInfo> {
    if (bookType) {
      return this.http.get<EpubBookInfo>(`${this.baseUrl}/${bookId}/info`, { params: { bookType } });
    }
    return this.http.get<EpubBookInfo>(`${this.baseUrl}/${bookId}/info`);
  }

  getBaseUrl(): string {
    return this.baseUrl;
  }

  getAuthToken(): string | null {
    return this.authService.getInternalAccessToken();
  }
}
