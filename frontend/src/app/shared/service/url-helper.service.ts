import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../core/config/api-config';
import {AuthService} from './auth.service';
import {Router} from '@angular/router';
import {Book, BookType} from '../../features/book/model/book.model';

@Injectable({
  providedIn: 'root'
})
export class UrlHelperService {
  private readonly baseUrl = API_CONFIG.BASE_URL;
  private readonly mediaBaseUrl = `${this.baseUrl}/api/v1/media`;
  private authService = inject(AuthService);
  private router = inject(Router);

  private getToken(): string | null {
    return this.authService.getInternalAccessToken();
  }

  private appendToken(url: string): string {
    const token = this.getToken();
    return token ? `${url}${url.includes('?') ? '&' : '?'}token=${token}` : url;
  }

  getThumbnailUrl(bookId: number, coverUpdatedOn?: string, width?: number): string | null {
    if (!coverUpdatedOn) return null;
    const sizeSuffix = width && width > 0 ? `&w=${width}` : '';
    return this.appendToken(`${this.mediaBaseUrl}/book/${bookId}/thumbnail?${coverUpdatedOn}${sizeSuffix}`);
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

  getAudiobookThumbnailUrl(bookId: number, audiobookCoverUpdatedOn?: string, width?: number): string | null {
    if (!audiobookCoverUpdatedOn) return null;
    const sizeSuffix = width && width > 0 ? `&w=${width}` : '';
    return this.appendToken(`${this.mediaBaseUrl}/book/${bookId}/audiobook-thumbnail?${audiobookCoverUpdatedOn}${sizeSuffix}`);
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

  getBookUrl(book: Book) {
    return this.router.createUrlTree(['/book', book.id], {
      queryParams: {tab: 'view'}
    });
  }

  getBookPrimaryReadingUrl(book: Book) {
    const bookType: BookType | undefined = book.primaryFile?.bookType;

    let baseUrl: string | null = null;

    switch (bookType) {
      case 'PDF':
        baseUrl = 'pdf-reader';
        break;

      case 'EPUB':
      case 'FB2':
      case 'MOBI':
      case 'AZW3':
        baseUrl = 'ebook-reader';
        break;

      case 'CBX':
        baseUrl = 'cbx-reader';
        break;

      case 'AUDIOBOOK':
        baseUrl = 'audiobook-player';
        break;
    }

    if (!baseUrl) {
      console.error('Unsupported book type:', bookType);
      //Go to book URL as a fall-back
      return this.getBookUrl(book);
    }

    return this.router.createUrlTree([`/${baseUrl}/book/${book.id}`], undefined);
  }

  filterBooksBy(filterKey: string, filterValue: string) {
    if (filterKey === 'series') {
      return this.router.createUrlTree(['/series', filterValue])
    }

    return this.router.createUrlTree(['/all-books'], {
      queryParams: {
        view: 'grid',
        sort: 'title',
        direction: 'asc',
        sidebar: true,
        filter: `${filterKey}:${encodeURIComponent(filterValue)}`
      }
    });
  }
}
